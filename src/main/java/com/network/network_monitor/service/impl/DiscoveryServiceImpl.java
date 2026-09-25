package com.network.network_monitor.service.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.network.network_monitor.config.DiscoveryLimits;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.DiscoveryOutcome;
import com.network.network_monitor.dto.ScanRequestDto;
import com.network.network_monitor.dto.ScanResponseDto;
import com.network.network_monitor.exception.InvalidCidrException;
import com.network.network_monitor.exception.ScanCapacityException;
import com.network.network_monitor.exception.ScanTooLargeException;
import com.network.network_monitor.service.DiscoveryService;
import com.network.network_monitor.util.CidrValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

	private final DiscoveryTransactionService transactionService;
	private final MonitoringDefaults defaults;
	private final DiscoveryLimits limits;

	// -------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------

	@Override
	public ScanResponseDto scanNetwork(ScanRequestDto request) {
		// 1. Resolve subnet (validate or auto-detect)
		String targetSubnet = resolveSubnet(request.getSubnet());

		// 2. Parse and generate IP list — throws before any task creation
		List<String> targetIps = buildHostList(targetSubnet);

		// 3. Guard concurrent scans
		if (!getScanSemaphore().tryAcquire()) {
			throw new ScanCapacityException(limits.getMaxConcurrentScans());
		}
		try {
			return runScan(targetSubnet, targetIps);
		} finally {
			getScanSemaphore().release();
		}
	}

	// -------------------------------------------------------------------
	// Subnet resolution
	// -------------------------------------------------------------------

	private String resolveSubnet(String raw) {
		if (raw != null && !raw.isBlank()) {
			return CidrValidator.normalise(raw);
		}
		return detectLocalSubnet();
	}

	// -------------------------------------------------------------------
	// Host list generation with size guard
	// -------------------------------------------------------------------

	private List<String> buildHostList(String cidr) {
		int slashIdx = cidr.indexOf('/');
		int prefix = Integer.parseInt(cidr.substring(slashIdx + 1));
		int networkInt = CidrValidator.parseStrictIpv4(cidr.substring(0, slashIdx));
		int mask = (prefix == 32) ? 0xFFFFFFFF : (0xFFFFFFFF << (32 - prefix));
		networkInt = networkInt & mask;

		List<String> ips = new ArrayList<>();

		if (prefix == 32) {
			// Single host
			ips.add(CidrValidator.intToIpv4(networkInt));
		} else if (prefix == 31) {
			// RFC 3021: both addresses usable
			ips.add(CidrValidator.intToIpv4(networkInt));
			ips.add(CidrValidator.intToIpv4(networkInt + 1));
		} else {
			// Normal subnet: skip network address and broadcast
			int startIp = networkInt + 1;
			int endIp = (networkInt | ~mask) - 1;
			int count = endIp - startIp + 1;
			if (count > limits.getMaxHostsPerScan()) {
				throw new ScanTooLargeException(count, limits.getMaxHostsPerScan());
			}
			for (int i = startIp; i <= endIp; i++) {
				ips.add(CidrValidator.intToIpv4(i));
			}
		}
		return ips;
	}

	// -------------------------------------------------------------------
	// Scan execution with bounded thread pool + overall timeout
	// -------------------------------------------------------------------

	private ScanResponseDto runScan(String subnet, List<String> ips) {
		List<ScanResponseDto.DiscoveredDevice> discoveredDevices = Collections.synchronizedList(new ArrayList<>());

		// Bounded fixed pool — không tạo virtual thread không giới hạn
		ExecutorService probePool = Executors
				.newFixedThreadPool(Math.min(limits.getProbeConcurrency(), ips.size() > 0 ? ips.size() : 1));

		List<Future<?>> futures = new ArrayList<>(ips.size());
		boolean timedOut = false;
		try {
			for (String ip : ips) {
				futures.add(probePool.submit(() -> {
					ScanResponseDto.DiscoveredDevice dev = probe(ip);
					if (dev != null)
						discoveredDevices.add(dev);
				}));
			}

			// Chờ tất cả hoàn thành hoặc timeout tổng
			probePool.shutdown();
			timedOut = !probePool.awaitTermination(limits.getScanTimeoutSeconds(), TimeUnit.SECONDS);
			if (timedOut) {
				probePool.shutdownNow();
				log.warn("Scan subnet {} timeout sau {}s — trả kết quả một phần ({}/{} host).", subnet,
						limits.getScanTimeoutSeconds(), discoveredDevices.size(), ips.size());
			}
		} catch (InterruptedException e) {
			probePool.shutdownNow();
			Thread.currentThread().interrupt();
			log.warn("Scan subnet {} bị ngắt.", subnet);
		}

		ScanResponseDto response = persistDiscoveredDevices(discoveredDevices, subnet);
		if (timedOut) {
			response.setPartial(true);
		}
		return response;
	}

	// -------------------------------------------------------------------
	// Persist (transaction-free; each IP has its own REQUIRES_NEW tx)
	// -------------------------------------------------------------------

	ScanResponseDto persistDiscoveredDevices(List<ScanResponseDto.DiscoveredDevice> devices, String subnet) {

		ScanResponseDto response = ScanResponseDto.builder().discovered(devices.size()).details(devices).build();

		for (ScanResponseDto.DiscoveredDevice device : devices) {
			DiscoveryOutcome outcome;
			try {
				// Mỗi IP có REQUIRES_NEW transaction riêng → lỗi 1 IP không rollback IP khác
				outcome = transactionService.processDiscoveredDevice(device, subnet);
			} catch (DataIntegrityViolationException e) {
				// Hai scan đồng thời cố insert cùng IP — không phải lỗi nghiêm trọng
				log.warn("Discovery IP {}: constraint violation (concurrent scan?) — skipping", device.getIpAddress());
				outcome = DiscoveryOutcome.SKIPPED;
			} catch (ConcurrencyFailureException e) {
				log.warn("Discovery IP {}: concurrency failure — skipping", device.getIpAddress());
				outcome = DiscoveryOutcome.SKIPPED;
			} catch (RuntimeException e) {
				log.error("Discovery IP {}: unexpected error", device.getIpAddress(), e);
				outcome = DiscoveryOutcome.FAILED;
			}
			device.setOutcome(outcome);
			device.setNew(outcome == DiscoveryOutcome.ADDED);
			switch (outcome) {
			case ADDED -> response.setAdded(response.getAdded() + 1);
			case REACTIVATED -> response.setReactivated(response.getReactivated() + 1);
			case SKIPPED -> response.setSkipped(response.getSkipped() + 1);
			case FAILED -> response.setFailed(response.getFailed() + 1);
			}
		}
		return response;
	}

	// -------------------------------------------------------------------
	// Probe: ICMP ping (no DB transaction) + ARP MAC lookup
	// -------------------------------------------------------------------

	/**
	 * Thực hiện ICMP reachability check bên ngoài transaction. Nếu host phản hồi →
	 * lấy MAC via ARP.
	 */
	private ScanResponseDto.DiscoveredDevice probe(String ip) {
		try {
			// InetAddress.getByAddress không resolve hostname
			byte[] addr = ipToBytes(ip);
			InetAddress inet = InetAddress.getByAddress(addr);

			long start = System.currentTimeMillis();
			boolean reachable = inet.isReachable(limits.getProbeTimeoutMs());
			if (!reachable)
				return null;

			long latency = System.currentTimeMillis() - start;
			String mac = getMacAddress(ip);
			return ScanResponseDto.DiscoveredDevice.builder().ipAddress(ip).macAddress(mac).latencyMs(latency).build();
		} catch (Exception e) {
			log.debug("probe({}): {}", ip, e.getMessage());
			return null;
		}
	}

	/**
	 * Chuyển dotted-decimal string sang byte[4] để dùng với
	 * InetAddress.getByAddress.
	 */
	private byte[] ipToBytes(String ip) {
		String[] parts = ip.split("\\.");
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i++) {
			bytes[i] = (byte) Integer.parseInt(parts[i]);
		}
		return bytes;
	}

	// -------------------------------------------------------------------
	// ARP: ProcessBuilder với argument riêng (không ghép chuỗi)
	// -------------------------------------------------------------------

	private static final Pattern MAC_PATTERN = Pattern.compile("([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}");

	private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	/**
	 * Lấy MAC từ ARP cache bằng ProcessBuilder — không ghép chuỗi ip vào command.
	 * Hủy process nếu vượt timeout.
	 */
	private String getMacAddress(String ip) {
		// Validate ip trước khi đưa vào ProcessBuilder (đã được validate ở
		// buildHostList)
		ProcessBuilder pb;
		if (IS_WINDOWS) {
			pb = new ProcessBuilder("arp", "-a", ip);
		} else {
			// Linux: arp -n <ip>
			pb = new ProcessBuilder("arp", "-n", ip);
		}
		pb.redirectErrorStream(true);

		Process process = null;
		try {
			process = pb.start();
			boolean finished = process.waitFor(limits.getArpTimeoutMs(), TimeUnit.MILLISECONDS);
			if (!finished) {
				log.debug("ARP timeout cho IP {}", ip);
				return null;
			}
			try (InputStream is = process.getInputStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Matcher m = MAC_PATTERN.matcher(line);
					if (m.find()) {
						return m.group().replace("-", ":").toUpperCase();
					}
				}
			}
		} catch (Exception e) {
			log.debug("getMacAddress({}): {}", ip, e.getMessage());
		} finally {
			if (process != null) {
				process.destroy();
			}
		}
		return null;
	}

	// -------------------------------------------------------------------
	// Auto-detect local subnet
	// -------------------------------------------------------------------

	/**
	 * Phát hiện subnet của interface phù hợp theo quy tắc rõ ràng:
	 * <ol>
	 * <li>Bỏ qua loopback, down, link-local (169.254/16)</li>
	 * <li>Ưu tiên interface không phải virtual (không tên "vir", "vmnet",
	 * "docker")</li>
	 * <li>Nếu chỉ có 1 interface hợp lệ → dùng luôn</li>
	 * <li>Nếu có nhiều → throw để yêu cầu client chỉ định subnet</li>
	 * </ol>
	 */
	private String detectLocalSubnet() {
		List<String> candidates = new ArrayList<>();
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces != null && interfaces.hasMoreElements()) {
				NetworkInterface ni = interfaces.nextElement();
				if (ni.isLoopback() || !ni.isUp())
					continue;

				String name = ni.getName().toLowerCase();
				// Bỏ interface ảo phổ biến
				if (name.startsWith("vir") || name.startsWith("vmnet") || name.startsWith("docker")
						|| name.startsWith("veth") || name.startsWith("br-"))
					continue;

				for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
					InetAddress addr = ia.getAddress();
					if (!(addr instanceof Inet4Address))
						continue;

					// Bỏ link-local (169.254.x.x)
					byte[] raw = addr.getAddress();
					if ((raw[0] & 0xFF) == 169 && (raw[1] & 0xFF) == 254)
						continue;

					int prefix = ia.getNetworkPrefixLength();
					if (prefix < 1 || prefix > 32)
						continue;

					int ipInt = bytesToInt(raw);
					int mask = (prefix == 32) ? 0xFFFFFFFF : (0xFFFFFFFF << (32 - prefix));
					int netInt = ipInt & mask;
					candidates.add(CidrValidator.intToIpv4(netInt) + "/" + prefix);
				}
			}
		} catch (Exception e) {
			log.warn("detectLocalSubnet: error enumerating interfaces", e);
		}

		if (candidates.isEmpty()) {
			throw new InvalidCidrException("SUBNET_NOT_FOUND",
					"Không tìm thấy interface mạng phù hợp. Vui lòng truyền subnet vào yêu cầu.");
		}
		if (candidates.size() == 1) {
			log.info("Auto-detected subnet: {}", candidates.get(0));
			return candidates.get(0);
		}
		// Nhiều candidate → yêu cầu client chọn
		throw new InvalidCidrException("SUBNET_AMBIGUOUS",
				"Phát hiện nhiều interface hợp lệ: " + candidates + ". Vui lòng truyền subnet cụ thể vào yêu cầu.");
	}

	private int bytesToInt(byte[] b) {
		return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
	}

	// -------------------------------------------------------------------
	// Semaphore lazy-init (đọc limits sau khi Spring inject xong)
	// -------------------------------------------------------------------

	/** Lazy double-checked semaphore — khởi tạo sau khi Spring inject limits. */
	private volatile Semaphore realSemaphore;

	private Semaphore getScanSemaphore() {
		if (realSemaphore == null) {
			synchronized (this) {
				if (realSemaphore == null) {
					realSemaphore = new Semaphore(limits.getMaxConcurrentScans(), true);
				}
			}
		}
		return realSemaphore;
	}
}
