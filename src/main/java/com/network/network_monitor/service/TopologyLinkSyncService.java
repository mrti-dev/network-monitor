package com.network.network_monitor.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.LldpNeighborDto;
import com.network.network_monitor.dto.TopologyDiscoveryResultDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.NetworkLink;
import com.network.network_monitor.enums.LinkDiscoverySource;
import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.NetworkLinkRepository;
import com.network.network_monitor.util.LldpNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyLinkSyncService {

    private final DeviceRepository deviceRepository;
    private final NetworkLinkRepository networkLinkRepository;

    @Transactional
    public TopologyDiscoveryResultDto syncDiscoveredLinks(Map<Long, List<LldpNeighborDto>> deviceNeighborsMap,
                                                          int scannedDevices,
                                                          int failedDevices,
                                                          int unsupportedDevices,
                                                          boolean hasValidDiscovery) {
        List<Device> allDevices = deviceRepository.findAll();
        Map<Long, Device> deviceMap = new HashMap<>();
        for (Device d : allDevices) {
            if (!Boolean.TRUE.equals(d.getIsDeleted())) {
                deviceMap.put(d.getId(), d);
            }
        }

        List<NetworkLink> existingLinks = networkLinkRepository.findAll();
        Map<LinkKey, NetworkLink> linkMap = new HashMap<>();
        for (NetworkLink link : existingLinks) {
            LinkKey key = makeLinkKey(link.getSourceDevice().getId(), link.getSourcePort(),
                    link.getTargetDevice().getId(), link.getTargetPort());
            linkMap.put(key, link);
        }

        Set<Long> discoveredLinkIds = new HashSet<>();
        Set<LinkKey> processedLinksInThisRun = new HashSet<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (Map.Entry<Long, List<LldpNeighborDto>> entry : deviceNeighborsMap.entrySet()) {
            Long srcId = entry.getKey();
            Device srcDevice = deviceMap.get(srcId);
            if (srcDevice == null) continue;

            for (LldpNeighborDto neighbor : entry.getValue()) {
                Device tgtDevice = matchTargetDevice(neighbor, deviceMap.values().stream().toList(), srcDevice);
                if (tgtDevice == null) continue;

                // Chuẩn hóa canonical thứ tự src < tgt
                Device canonicalSrc = srcDevice;
                Device canonicalTgt = tgtDevice;
                String canonicalSrcPort = LldpNormalizer.normalizePort(neighbor.getLocalPort());
                String canonicalTgtPort = LldpNormalizer.normalizePort(
                        neighbor.getRemotePortId() != null && !neighbor.getRemotePortId().isBlank()
                                ? neighbor.getRemotePortId()
                                : neighbor.getRemotePortDesc()
                );

                if (srcDevice.getId() > tgtDevice.getId()) {
                    canonicalSrc = tgtDevice;
                    canonicalTgt = srcDevice;
                    canonicalSrcPort = canonicalTgtPort;
                    canonicalTgtPort = LldpNormalizer.normalizePort(neighbor.getLocalPort());
                }

                LinkKey linkKey = makeLinkKey(canonicalSrc.getId(), canonicalSrcPort,
                        canonicalTgt.getId(), canonicalTgtPort);
                if (!processedLinksInThisRun.add(linkKey)) continue;

                NetworkLink existing = linkMap.get(linkKey);
                if (existing != null) {
                    if (existing.getDiscoverySource() == LinkDiscoverySource.MANUAL) {
                        log.debug("Bỏ qua link MANUAL giữa thiết bị {} và {}", canonicalSrc.getId(), canonicalTgt.getId());
                        continue;
                    }

                    existing.setStatus(LinkStatus.UP);
                    if (existing.getBandwidthMbps() == null || existing.getBandwidthMbps() <= 0) {
                        existing.setBandwidthMbps(1000);
                    }
                    existing.setLastDiscoveredAt(LocalDateTime.now());
                    NetworkLink saved = networkLinkRepository.save(existing);
                    discoveredLinkIds.add(saved.getId());
                    linkMap.put(linkKey, saved);
                    updatedCount++;
                } else {
                    NetworkLink newLink = NetworkLink.builder()
                            .sourceDevice(canonicalSrc)
                            .targetDevice(canonicalTgt)
                            .linkType(LinkType.ETHERNET)
                            .sourcePort(canonicalSrcPort)
                            .targetPort(canonicalTgtPort)
                            .bandwidthMbps(1000)
                            .status(LinkStatus.UP)
                            .discoverySource(LinkDiscoverySource.LLDP)
                            .lastDiscoveredAt(LocalDateTime.now())
                            .build();
                    NetworkLink saved = networkLinkRepository.save(newLink);
                    discoveredLinkIds.add(saved.getId());
                    linkMap.put(linkKey, saved);
                    createdCount++;
                }
            }
        }

        if (hasValidDiscovery) {
            List<NetworkLink> allLldpLinks = networkLinkRepository.findByDiscoverySource(LinkDiscoverySource.LLDP);
            for (NetworkLink link : allLldpLinks) {
                boolean sourceScanned = deviceNeighborsMap.containsKey(link.getSourceDevice().getId());
                boolean targetScanned = deviceNeighborsMap.containsKey(link.getTargetDevice().getId());
                if ((sourceScanned || targetScanned)
                        && !discoveredLinkIds.contains(link.getId()) && link.getStatus() != LinkStatus.STALE) {
                    link.setStatus(LinkStatus.STALE);
                    networkLinkRepository.save(link);
                    log.info("Chuyển link id={} ({} <-> {}) sang trạng thái STALE",
                            link.getId(), link.getSourceDevice().getName(), link.getTargetDevice().getName());
                }
            }
        }

        return TopologyDiscoveryResultDto.builder()
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .scannedDevices(scannedDevices)
                .failedDevices(failedDevices)
                .unsupportedDevices(unsupportedDevices)
                .build();
    }

    public Device matchTargetDevice(LldpNeighborDto neighbor, List<Device> candidates, Device sourceDevice) {
        if (neighbor == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 1. Đối chiếu theo Management IP
        String normMgmtIp = LldpNormalizer.normalizeIp(neighbor.getRemoteManagementIp());
        if (normMgmtIp != null) {
            List<Device> ipMatches = candidates.stream()
                    .filter(d -> Objects.equals(LldpNormalizer.normalizeIp(d.getIpAddress()), normMgmtIp))
                    .toList();
            if (ipMatches.size() == 1) {
                Device matched = ipMatches.get(0);
                if (Objects.equals(matched.getId(), sourceDevice.getId())) {
                    return null;
                }
                return matched;
            } else if (ipMatches.size() > 1) {
                log.warn("Đối chiếu IP '{}' từ LLDP trùng lặp {} thiết bị, bỏ qua ghép nối", normMgmtIp, ipMatches.size());
                return null;
            }
        }

        // 2. Đối chiếu theo MAC / Chassis ID
        String normMac = LldpNormalizer.normalizeMac(neighbor.getRemoteChassisMac());
        if (normMac == null && neighbor.getRemoteChassisId() != null) {
            normMac = LldpNormalizer.normalizeMac(neighbor.getRemoteChassisId());
        }
        if (normMac != null) {
            String finalNormMac = normMac;
            List<Device> macMatches = candidates.stream()
                    .filter(d -> Objects.equals(LldpNormalizer.normalizeMac(d.getMacAddress()), finalNormMac))
                    .toList();
            if (macMatches.size() == 1) {
                Device matched = macMatches.get(0);
                if (Objects.equals(matched.getId(), sourceDevice.getId())) {
                    return null;
                }
                return matched;
            } else if (macMatches.size() > 1) {
                log.warn("Đối chiếu MAC '{}' từ LLDP trùng lặp {} thiết bị, bỏ qua ghép nối", finalNormMac, macMatches.size());
                return null;
            }
        }

        // 3. Đối chiếu theo sysName / Device name
        String normSysName = LldpNormalizer.normalizeHostname(neighbor.getRemoteSysName());
        if (normSysName != null) {
            String shortSysName = LldpNormalizer.extractShortHostname(normSysName);
            List<Device> nameMatches = candidates.stream()
                    .filter(d -> {
                        String devName = LldpNormalizer.normalizeHostname(d.getName());
                        if (devName == null) return false;
                        if (devName.equals(normSysName)) return true;
                        String devShortName = LldpNormalizer.extractShortHostname(devName);
                        return shortSysName != null && shortSysName.equals(devShortName);
                    })
                    .toList();
            if (nameMatches.size() == 1) {
                Device matched = nameMatches.get(0);
                if (Objects.equals(matched.getId(), sourceDevice.getId())) {
                    return null;
                }
                return matched;
            } else if (nameMatches.size() > 1) {
                log.warn("Đối chiếu sysName '{}' từ LLDP trùng lặp {} thiết bị, bỏ qua ghép nối", normSysName, nameMatches.size());
                return null;
            }
        }

        return null;
    }

    private LinkKey makeLinkKey(Long sourceId, String sourcePort, Long targetId, String targetPort) {
        String firstPort = LldpNormalizer.normalizePort(sourcePort);
        String secondPort = LldpNormalizer.normalizePort(targetPort);
        if (sourceId > targetId) {
            return new LinkKey(targetId, secondPort, sourceId, firstPort);
        }
        return new LinkKey(sourceId, firstPort, targetId, secondPort);
    }

    private record LinkKey(Long sourceId, String sourcePort, Long targetId, String targetPort) {}
}
