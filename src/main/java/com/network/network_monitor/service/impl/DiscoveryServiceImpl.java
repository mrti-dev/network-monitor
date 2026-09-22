package com.network.network_monitor.service.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.ScanRequestDto;
import com.network.network_monitor.dto.ScanResponseDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceLogAction;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.DiscoveryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryServiceImpl implements DiscoveryService {

    private final DeviceRepository deviceRepository;
    private final DeviceLogRepository deviceLogRepository;

    @Override
    @Transactional
    public ScanResponseDto scanNetwork(ScanRequestDto request) {
        String targetSubnet = request.getSubnet();
        if (targetSubnet == null || targetSubnet.trim().isEmpty()) {
            targetSubnet = detectLocalSubnet();
            log.info("Không có Subnet truyền vào, tự động phát hiện dải mạng LAN: {}", targetSubnet);
        }
        
        List<String> targetIps = getIpsInSubnet(targetSubnet);
        
        List<ScanResponseDto.DiscoveredDevice> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (String ip : targetIps) {
                futures.add(executor.submit(() -> {
                    ScanResponseDto.DiscoveredDevice dev = probe(ip);
                    if (dev != null) {
                        discoveredDevices.add(dev);
                    }
                    return null;
                }));
            }
            // Wait for all to finish
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("Lỗi khi quét IP", e);
                }
            }
        }
        
        int added = 0;
        int skipped = 0;
        
        for (ScanResponseDto.DiscoveredDevice dev : discoveredDevices) {
            if (deviceRepository.existsByIpAddress(dev.getIpAddress())) {
                dev.setNew(false);
                skipped++;
            } else {
                dev.setNew(true);
                enrollDevice(dev, targetSubnet);
                added++;
            }
        }
        
        return ScanResponseDto.builder()
                .discovered(discoveredDevices.size())
                .added(added)
                .skipped(skipped)
                .details(discoveredDevices)
                .build();
    }
    
    private void enrollDevice(ScanResponseDto.DiscoveredDevice dev, String subnet) {
        Device device = Device.builder()
                .name("Auto-Discovered: " + dev.getIpAddress())
                .ipAddress(dev.getIpAddress())
                .macAddress(dev.getMacAddress())
                .status(DeviceStatus.UNKNOWN)
                .isMonitored(true)
                .isDeleted(false)
                .build();
                
        MonitoringConfig config = MonitoringConfig.builder()
                .device(device)
                .pingInterval(15)
                .timeoutMs(2000)
                .latencyThreshold(150.0)
                .strategyType(ProbingMethod.ICMP)
                .build();
        device.setMonitoringConfig(config);
        
        deviceRepository.save(device);
        
        DeviceLog logEntry = DeviceLog.builder()
                .device(device)
                .action(DeviceLogAction.AUTO_DISCOVER)
                .description("Discovered via sweep " + subnet)
                .createdAt(LocalDateTime.now())
                .build();
        deviceLogRepository.save(logEntry);
    }
    
    private ScanResponseDto.DiscoveredDevice probe(String ip) {
        try {
            long startTime = System.currentTimeMillis();
            InetAddress inet = InetAddress.getByName(ip);
            if (inet.isReachable(2000)) {
                long latency = System.currentTimeMillis() - startTime;
                String mac = getMacAddress(ip);
                return ScanResponseDto.DiscoveredDevice.builder()
                        .ipAddress(ip)
                        .macAddress(mac)
                        .latencyMs(latency)
                        .build();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    private String getMacAddress(String ip) {
        try {
            Process p = Runtime.getRuntime().exec("arp -a " + ip);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            Pattern pattern = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    return m.group().replace("-", ":").toUpperCase();
                }
            }
        } catch (Exception e) {
            log.warn("Không lấy được MAC của {}", ip);
        }
        return null;
    }

    private List<String> getIpsInSubnet(String subnet) {
        String[] parts = subnet.split("/");
        String ip = parts[0];
        int prefix = Integer.parseInt(parts[1]);

        int ipInt = inetAddressToInt(ip);
        int mask = 0xFFFFFFFF << (32 - prefix);
        int startIp = (ipInt & mask) + 1;
        int endIp = (ipInt | ~mask) - 1;

        List<String> ips = new ArrayList<>();
        for (int i = startIp; i <= endIp; i++) {
            ips.add(intToInetAddress(i));
        }
        return ips;
    }

    private int inetAddressToInt(String ipStr) {
        try {
            byte[] bytes = InetAddress.getByName(ipStr).getAddress();
            int val = 0;
            for (int i = 0; i < bytes.length; i++) {
                val <<= 8;
                val |= bytes[i] & 0xff;
            }
            return val;
        } catch (Exception e) { return 0; }
    }

    private String intToInetAddress(int val) {
        try {
            byte[] bytes = new byte[4];
            for (int i = 3; i >= 0; i--) {
                bytes[i] = (byte) (val & 0xff);
                val >>= 8;
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) { return ""; }
    }

    private String detectLocalSubnet() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
                
                for (java.net.InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
                    InetAddress inetAddress = address.getAddress();
                    if (inetAddress instanceof java.net.Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        int prefixLength = address.getNetworkPrefixLength();
                        
                        // Calculate base network ip
                        int mask = 0xffffffff << (32 - prefixLength);
                        int ipInt = inetAddressToInt(ip);
                        int networkIp = ipInt & mask;
                        
                        return intToInetAddress(networkIp) + "/" + prefixLength;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi khi tự động dò tìm Subnet: ", e);
        }
        return "192.168.1.0/24"; // Fallback default
    }
}
