package com.network.network_monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.LldpNeighborDto;
import com.network.network_monitor.dto.NetworkLinkDto;
import com.network.network_monitor.dto.NetworkLinkFormDto;
import com.network.network_monitor.dto.TopologyDataDto;
import com.network.network_monitor.dto.TopologyDiscoveryResultDto;
import com.network.network_monitor.dto.TopologyNodeDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.entity.NetworkLink;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.entity.SnmpMetricLog;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.LinkDiscoverySource;
import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.repository.NetworkLinkRepository;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.service.TopologyLinkSyncService;
import com.network.network_monitor.service.TopologyService;
import com.network.network_monitor.strategy.LldpProbeResult;
import com.network.network_monitor.strategy.SnmpProbingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyServiceImpl implements TopologyService {

    private final DeviceRepository deviceRepository;
    private final NetworkLinkRepository networkLinkRepository;
    private final MetricLogRepository metricLogRepository;
    private final SnmpMetricLogRepository snmpMetricLogRepository;
    private final SnmpConfigRepository snmpConfigRepository;
    private final SnmpProbingStrategy snmpProbingStrategy;
    private final TopologyLinkSyncService topologyLinkSyncService;

    @Override
    @Transactional(readOnly = true)
    public TopologyDataDto getTopologyData() {
        List<Device> devices = deviceRepository.findAll();
        Map<Long, Device> deviceMap = devices.stream().collect(Collectors.toMap(Device::getId, d -> d));

        List<TopologyNodeDto> nodes = new ArrayList<>();
        int onlineCount = 0;
        int offlineCount = 0;

        for (Device d : devices) {
            Double latency = null;
            Double loss = null;
            List<MetricLog> logs = metricLogRepository.findTop30ByDeviceIdOrderByRecordedAtDescIdDesc(d.getId());
            if (!logs.isEmpty()) {
                latency = logs.get(0).getLatencyMs();
                loss = logs.get(0).getPacketLoss();
            }

            Double cpu = null;
            Double mem = null;
            List<SnmpMetricLog> snmpLogs = snmpMetricLogRepository.findLatestByDeviceId(d.getId(), PageRequest.of(0, 1));
            if (!snmpLogs.isEmpty()) {
                cpu = snmpLogs.get(0).getCpuUsage();
                mem = snmpLogs.get(0).getMemoryUsage();
            }

            if (d.getStatus() == DeviceStatus.ONLINE) onlineCount++;
            else if (d.getStatus() == DeviceStatus.OFFLINE) offlineCount++;

            nodes.add(TopologyNodeDto.builder()
                    .id(d.getId())
                    .name(d.getName())
                    .ipAddress(d.getIpAddress())
                    .macAddress(d.getMacAddress())
                    .deviceType(d.getDeviceType())
                    .status(d.getStatus())
                    .location(d.getLocation())
                    .groupName(d.getGroup() != null ? d.getGroup().getName() : null)
                    .isMonitored(d.getIsMonitored())
                    .latencyMs(latency)
                    .packetLoss(loss)
                    .cpuUsage(cpu)
                    .memoryUsage(mem)
                    .build());
        }

        List<NetworkLink> links = networkLinkRepository.findActiveLinks();
        List<NetworkLinkDto> linkDtos = new ArrayList<>();
        int upCount = 0;
        int downCount = 0;

        for (NetworkLink link : links) {
            Device src = deviceMap.get(link.getSourceDevice().getId());
            Device tgt = deviceMap.get(link.getTargetDevice().getId());

            LinkStatus effectiveStatus = link.getStatus();
            if (effectiveStatus != LinkStatus.STALE && src != null && tgt != null) {
                if (src.getStatus() == DeviceStatus.OFFLINE || tgt.getStatus() == DeviceStatus.OFFLINE) {
                    effectiveStatus = LinkStatus.DOWN;
                } else if (src.getStatus() == DeviceStatus.WARNING || tgt.getStatus() == DeviceStatus.WARNING) {
                    effectiveStatus = LinkStatus.DEGRADED;
                }
            }

            if (effectiveStatus == LinkStatus.UP) upCount++;
            else if (effectiveStatus == LinkStatus.DOWN || effectiveStatus == LinkStatus.STALE) downCount++;

            linkDtos.add(NetworkLinkDto.builder()
                    .id(link.getId())
                    .sourceDeviceId(link.getSourceDevice().getId())
                    .sourceDeviceName(src != null ? src.getName() : "Unknown")
                    .sourceDeviceIp(src != null ? src.getIpAddress() : "")
                    .targetDeviceId(link.getTargetDevice().getId())
                    .targetDeviceName(tgt != null ? tgt.getName() : "Unknown")
                    .targetDeviceIp(tgt != null ? tgt.getIpAddress() : "")
                    .linkType(link.getLinkType())
                    .sourcePort(link.getSourcePort())
                    .targetPort(link.getTargetPort())
                    .bandwidthMbps(link.getBandwidthMbps())
                    .status(effectiveStatus)
                    .discoverySource(link.getDiscoverySource())
                    .lastDiscoveredAt(link.getLastDiscoveredAt())
                    .description(link.getDescription())
                    .build());
        }

        return TopologyDataDto.builder()
                .nodes(nodes)
                .links(linkDtos)
                .totalNodes(nodes.size())
                .onlineNodes(onlineCount)
                .offlineNodes(offlineCount)
                .totalLinks(linkDtos.size())
                .upLinks(upCount)
                .downLinks(downCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkLinkDto> getAllLinks() {
        return networkLinkRepository.findActiveLinks().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public NetworkLinkDto createLink(NetworkLinkFormDto form) {
        if (Objects.equals(form.getSourceDeviceId(), form.getTargetDeviceId())) {
            throw new IllegalArgumentException("Thiết bị nguồn và thiết bị đích không được trùng nhau");
        }

        Device src = deviceRepository.findById(form.getSourceDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị nguồn không tồn tại: " + form.getSourceDeviceId()));
        Device tgt = deviceRepository.findById(form.getTargetDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị đích không tồn tại: " + form.getTargetDeviceId()));

        // Kiểm tra liên kết trùng hai chiều theo thiết bị và interface
        List<NetworkLink> existingLinks = networkLinkRepository.findAllLinksBetweenDevices(src.getId(), tgt.getId());
        String srcPort = form.getSourcePort() != null ? form.getSourcePort().trim() : "";
        String tgtPort = form.getTargetPort() != null ? form.getTargetPort().trim() : "";

        boolean isDuplicate = existingLinks.stream().anyMatch(existing -> {
            String exSrcPort = existing.getSourcePort() != null ? existing.getSourcePort().trim() : "";
            String exTgtPort = existing.getTargetPort() != null ? existing.getTargetPort().trim() : "";

            boolean sameDirection = Objects.equals(existing.getSourceDevice().getId(), src.getId())
                    && Objects.equals(exSrcPort, srcPort)
                    && Objects.equals(exTgtPort, tgtPort);

            boolean reverseDirection = Objects.equals(existing.getSourceDevice().getId(), tgt.getId())
                    && Objects.equals(exSrcPort, tgtPort)
                    && Objects.equals(exTgtPort, srcPort);

            return sameDirection || reverseDirection;
        });

        if (isDuplicate) {
            throw new IllegalArgumentException("Liên kết giữa hai thiết bị và cổng tương ứng đã tồn tại trong hệ thống");
        }

        // Chuẩn hóa thứ tự source < target để tránh duplicate 2 chiều ở mức DB
        Device canonicalSrc = src;
        Device canonicalTgt = tgt;
        String canonicalSrcPort = srcPort;
        String canonicalTgtPort = tgtPort;

        if (src.getId() > tgt.getId()) {
            canonicalSrc = tgt;
            canonicalTgt = src;
            canonicalSrcPort = tgtPort;
            canonicalTgtPort = srcPort;
        }

        NetworkLink link = NetworkLink.builder()
                .sourceDevice(canonicalSrc)
                .targetDevice(canonicalTgt)
                .linkType(form.getLinkType() != null ? form.getLinkType() : LinkType.ETHERNET)
                .sourcePort(canonicalSrcPort)
                .targetPort(canonicalTgtPort)
                .bandwidthMbps(form.getBandwidthMbps() != null ? form.getBandwidthMbps() : 1000)
                .status(form.getStatus() != null ? form.getStatus() : LinkStatus.UP)
                .discoverySource(LinkDiscoverySource.MANUAL)
                .description(form.getDescription())
                .build();

        return toDto(networkLinkRepository.save(link));
    }

    @Override
    @Transactional
    public NetworkLinkDto updateLink(Long id, NetworkLinkFormDto form) {
        NetworkLink link = networkLinkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Liên kết không tồn tại: " + id));

        String sourcePort = form.getSourcePort() != null ? form.getSourcePort().trim() : normalizePort(link.getSourcePort());
        String targetPort = form.getTargetPort() != null ? form.getTargetPort().trim() : normalizePort(link.getTargetPort());
        boolean duplicate = networkLinkRepository
                .findAllLinksBetweenDevices(link.getSourceDevice().getId(), link.getTargetDevice().getId())
                .stream()
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .anyMatch(existing -> sameInterfaces(existing, link.getSourceDevice().getId(), sourcePort, targetPort));
        if (duplicate) {
            throw new IllegalArgumentException("Liên kết giữa hai thiết bị và cổng tương ứng đã tồn tại trong hệ thống");
        }

        if (form.getLinkType() != null) link.setLinkType(form.getLinkType());
        link.setSourcePort(sourcePort);
        link.setTargetPort(targetPort);
        if (form.getBandwidthMbps() != null) link.setBandwidthMbps(form.getBandwidthMbps());
        if (form.getStatus() != null) link.setStatus(form.getStatus());
        if (form.getDescription() != null) link.setDescription(form.getDescription());

        return toDto(networkLinkRepository.save(link));
    }

    @Override
    @Transactional
    public void deleteLink(Long id) {
        if (networkLinkRepository.existsById(id)) {
            networkLinkRepository.deleteById(id);
        }
    }

    @Override
    public int autoDiscoverLinks() {
        return autoDiscoverLinksDetailed().getCreatedCount();
    }

    @Override
    public TopologyDiscoveryResultDto autoDiscoverLinksDetailed() {
        List<SnmpConfig> configs = snmpConfigRepository.findActiveConfigs();
        int scannedDevices = configs.size();
        int failedDevices = 0;
        int unsupportedDevices = 0;
        int successScanCount = 0;

        Map<Long, List<LldpNeighborDto>> deviceNeighborsMap = new HashMap<>();

        for (SnmpConfig config : configs) {
            Device dev = config.getDevice();
            try {
                LldpProbeResult probe = snmpProbingStrategy.probeLldp(dev.getIpAddress(), config);
                if (!probe.isReachable()) {
                    failedDevices++;
                    log.warn("Device id={}, ip={}: Không thể thăm dò SNMP LLDP - {}",
                            dev.getId(), dev.getIpAddress(), probe.getFailureReason());
                } else if (!probe.isLldpSupported()) {
                    unsupportedDevices++;
                    log.info("Device id={}, ip={}: Thiết bị không hỗ trợ LLDP-MIB",
                            dev.getId(), dev.getIpAddress());
                } else {
                    successScanCount++;
                    deviceNeighborsMap.put(dev.getId(), probe.getNeighbors());
                }
            } catch (Exception e) {
                failedDevices++;
                log.warn("Device id={}, ip={}: Ngoại lệ khi thăm dò SNMP LLDP - {}",
                        dev.getId(), dev.getIpAddress(), e.getMessage());
            }
        }

        boolean hasValidDiscovery = successScanCount > 0;
        return topologyLinkSyncService.syncDiscoveredLinks(
                deviceNeighborsMap,
                scannedDevices,
                failedDevices,
                unsupportedDevices,
                hasValidDiscovery
        );
    }

    private boolean sameInterfaces(NetworkLink existing, Long sourceDeviceId,
                                   String sourcePort, String targetPort) {
        boolean sameDirection = Objects.equals(existing.getSourceDevice().getId(), sourceDeviceId)
                && Objects.equals(normalizePort(existing.getSourcePort()), sourcePort)
                && Objects.equals(normalizePort(existing.getTargetPort()), targetPort);
        boolean reverseDirection = !Objects.equals(existing.getSourceDevice().getId(), sourceDeviceId)
                && Objects.equals(normalizePort(existing.getSourcePort()), targetPort)
                && Objects.equals(normalizePort(existing.getTargetPort()), sourcePort);
        return sameDirection || reverseDirection;
    }

    private String normalizePort(String port) {
        return port == null ? "" : port.trim();
    }

    private NetworkLinkDto toDto(NetworkLink link) {
        return NetworkLinkDto.builder()
                .id(link.getId())
                .sourceDeviceId(link.getSourceDevice().getId())
                .sourceDeviceName(link.getSourceDevice().getName())
                .sourceDeviceIp(link.getSourceDevice().getIpAddress())
                .targetDeviceId(link.getTargetDevice().getId())
                .targetDeviceName(link.getTargetDevice().getName())
                .targetDeviceIp(link.getTargetDevice().getIpAddress())
                .linkType(link.getLinkType())
                .sourcePort(link.getSourcePort())
                .targetPort(link.getTargetPort())
                .bandwidthMbps(link.getBandwidthMbps())
                .status(link.getStatus())
                .discoverySource(link.getDiscoverySource())
                .lastDiscoveredAt(link.getLastDiscoveredAt())
                .description(link.getDescription())
                .build();
    }
}
