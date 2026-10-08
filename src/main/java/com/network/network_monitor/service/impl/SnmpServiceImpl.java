package com.network.network_monitor.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.SnmpConfigDto;
import com.network.network_monitor.dto.SnmpConfigFormDto;
import com.network.network_monitor.dto.SnmpInterfaceMetricDto;
import com.network.network_monitor.dto.SnmpMetricDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.entity.SnmpInterfaceMetric;
import com.network.network_monitor.entity.SnmpMetricLog;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.SnmpInterfaceStatus;
import com.network.network_monitor.enums.ServiceType;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.notification.SnmpMetricRecordedEvent;
import com.network.network_monitor.notification.ServiceAvailabilityRecordedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.repository.SnmpInterfaceMetricRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.service.SnmpService;
import com.network.network_monitor.strategy.SnmpProbeResult;
import com.network.network_monitor.strategy.SnmpProbingStrategy;
import com.network.network_monitor.util.SnmpCredentialEncryptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpServiceImpl implements SnmpService {

    private final SnmpConfigRepository snmpConfigRepository;
    private final SnmpMetricLogRepository metricLogRepository;
    private final SnmpInterfaceMetricRepository interfaceMetricRepository;
    private final DeviceRepository deviceRepository;
    private final SnmpProbingStrategy probingStrategy;
    private final SnmpCredentialEncryptor credentialEncryptor;
    private final AlertService alertService;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public SnmpConfigDto getConfigByDeviceId(Long deviceId) {
        return snmpConfigRepository.findByDeviceId(deviceId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    @Transactional
    public SnmpConfigDto saveConfig(Long deviceId, SnmpConfigFormDto form) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại: " + deviceId));

        if (Boolean.TRUE.equals(device.getIsDeleted())) {
            throw new IllegalStateException("Không thể cấu hình SNMP cho thiết bị đã bị xóa");
        }

        SnmpConfig config = snmpConfigRepository.findByDeviceId(deviceId).orElse(null);
        if (config == null) {
            if (form.getCommunity() == null || form.getCommunity().isBlank()) {
                throw new IllegalArgumentException("Community string không được để trống");
            }
            config = SnmpConfig.builder()
                    .device(device)
                    .version(form.getVersion())
                    .port(form.getPort())
                    .communityEncrypted(credentialEncryptor.encrypt(form.getCommunity().trim()))
                    .timeoutMs(form.getTimeoutMs())
                    .retries(form.getRetries())
                    .enabled(form.getEnabled() != null ? form.getEnabled() : true)
                    .build();
        } else {
            config.setVersion(form.getVersion());
            config.setPort(form.getPort());
            config.setTimeoutMs(form.getTimeoutMs());
            config.setRetries(form.getRetries());
            if (form.getEnabled() != null) {
                config.setEnabled(form.getEnabled());
            }
            if (form.getCommunity() != null && !form.getCommunity().isBlank() && !form.getCommunity().equals("******")) {
                config.setCommunityEncrypted(credentialEncryptor.encrypt(form.getCommunity().trim()));
            }
        }

        SnmpConfig saved = snmpConfigRepository.save(config);
        probingStrategy.clearCounterCache(deviceId);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void toggleSnmp(Long deviceId) {
        SnmpConfig config = snmpConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa cấu hình SNMP cho thiết bị: " + deviceId));
        config.setEnabled(!Boolean.TRUE.equals(config.getEnabled()));
        snmpConfigRepository.save(config);
    }

    @Override
    @Transactional
    public void deleteConfig(Long deviceId) {
        snmpConfigRepository.findByDeviceId(deviceId).ifPresent(config -> {
            snmpConfigRepository.delete(config);
            probingStrategy.clearCounterCache(deviceId);
        });
    }

    @Override
    public SnmpProbeResult testConnection(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại: " + deviceId));
        SnmpConfig config = snmpConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa cấu hình SNMP cho thiết bị: " + deviceId));

        return probingStrategy.probe(device.getIpAddress(), config);
    }

    @Override
    @Transactional(readOnly = true)
    public SnmpMetricDto getLatestMetric(Long deviceId) {
        List<SnmpMetricLog> logs = metricLogRepository.findLatestByDeviceId(deviceId, PageRequest.of(0, 1));
        return logs.isEmpty() ? null : toMetricDto(logs.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnmpMetricDto> getRecentMetrics(Long deviceId, int limit) {
        List<SnmpMetricLog> logs = metricLogRepository.findLatestByDeviceId(deviceId, PageRequest.of(0, Math.max(1, limit)));
        return logs.stream().map(this::toMetricDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SnmpInterfaceMetricDto> getLatestInterfaces(Long deviceId) {
        List<SnmpInterfaceMetric> list = interfaceMetricRepository.findLatestByDeviceId(deviceId, PageRequest.of(0, 100));
        // Lấy record mới nhất theo từng interfaceIndex
        Map<Integer, SnmpInterfaceMetric> latestMap = new LinkedHashMap<>();
        for (SnmpInterfaceMetric m : list) {
            latestMap.putIfAbsent(m.getInterfaceIndex(), m);
        }
        return latestMap.values().stream()
                .map(this::toInterfaceDto)
                .sorted((a, b) -> Integer.compare(a.getInterfaceIndex(), b.getInterfaceIndex()))
                .toList();
    }

    @Override
    @Transactional
    public void processProbeResult(Long deviceId, SnmpProbeResult result) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || Boolean.TRUE.equals(device.getIsDeleted())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        events.publishEvent(new ServiceAvailabilityRecordedEvent(
                device.getId(), ServiceType.SNMP, null, result.isReachable(), now));
        if (result.isReachable()) {
            alertService.resolveAlerts(device, AlertType.SNMP_UNREACHABLE);

            SnmpMetricLog metric = SnmpMetricLog.builder()
                    .device(device)
                    .sysUptime(result.getSysUptime())
                    .cpuUsage(result.getCpuUsage())
                    .memoryUsage(result.getMemoryUsage())
                    .inboundBps(result.getInboundBps())
                    .outboundBps(result.getOutboundBps())
                    .recordedAt(now)
                    .build();
            SnmpMetricLog savedMetric = metricLogRepository.save(metric);

            boolean hasDownInterface = false;
            if (result.getInterfaces() != null) {
                List<SnmpInterfaceMetric> ifMetrics = new ArrayList<>();
                for (SnmpProbeResult.InterfaceInfo ifInfo : result.getInterfaces()) {
                    ifMetrics.add(SnmpInterfaceMetric.builder()
                            .device(device)
                            .interfaceIndex(ifInfo.getIndex())
                            .interfaceName(ifInfo.getName())
                            .operationalStatus(ifInfo.getStatus())
                            .inboundOctets(ifInfo.getInboundOctets())
                            .outboundOctets(ifInfo.getOutboundOctets())
                            .recordedAt(now)
                            .build());
                    if (ifInfo.getStatus() == SnmpInterfaceStatus.DOWN) {
                        hasDownInterface = true;
                    }
                }
                interfaceMetricRepository.saveAll(ifMetrics);
            }

            if (hasDownInterface) {
                alertService.triggerAlert(device, "SNMP: Phát hiện giao diện mạng DOWN trên thiết bị " + device.getName(), AlertType.SNMP_INTERFACE_DOWN);
            } else {
                alertService.resolveAlerts(device, AlertType.SNMP_INTERFACE_DOWN);
            }

            if (result.getCpuUsage() != null && result.getCpuUsage() >= 90.0) {
                alertService.triggerAlert(device, String.format("SNMP: Tải CPU vượt ngưỡng: %.1f%%", result.getCpuUsage()), AlertType.CPU_OVERLOAD);
            } else if (result.getCpuUsage() != null && result.getCpuUsage() < 85.0) {
                alertService.resolveAlerts(device, AlertType.CPU_OVERLOAD);
            }

            if (result.getMemoryUsage() != null && result.getMemoryUsage() >= 90.0) {
                alertService.triggerAlert(device, String.format("SNMP: Tải RAM vượt ngưỡng: %.1f%%", result.getMemoryUsage()), AlertType.MEMORY_OVERLOAD);
            } else if (result.getMemoryUsage() != null && result.getMemoryUsage() < 85.0) {
                alertService.resolveAlerts(device, AlertType.MEMORY_OVERLOAD);
            }

            if (events != null) {
                events.publishEvent(new SnmpMetricRecordedEvent(
                        savedMetric.getId(),
                        device.getId(),
                        savedMetric.getSysUptime(),
                        savedMetric.getCpuUsage(),
                        savedMetric.getMemoryUsage(),
                        savedMetric.getInboundBps(),
                        savedMetric.getOutboundBps(),
                        savedMetric.getRecordedAt()
                ));
            }
        } else {
            String reason = result.getFailureReason() != null ? result.getFailureReason() : "Timeout";
            alertService.triggerAlert(device, "SNMP: Không thể kết nối tới thiết bị " + device.getName() + ": " + reason, AlertType.SNMP_UNREACHABLE);
        }
    }

    private SnmpConfigDto toDto(SnmpConfig entity) {
        return SnmpConfigDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .deviceName(entity.getDevice().getName())
                .ipAddress(entity.getDevice().getIpAddress())
                .version(entity.getVersion())
                .port(entity.getPort())
                .community("******")
                .timeoutMs(entity.getTimeoutMs())
                .retries(entity.getRetries())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private SnmpMetricDto toMetricDto(SnmpMetricLog entity) {
        return SnmpMetricDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .sysUptime(entity.getSysUptime())
                .cpuUsage(entity.getCpuUsage())
                .memoryUsage(entity.getMemoryUsage())
                .inboundBps(entity.getInboundBps())
                .outboundBps(entity.getOutboundBps())
                .recordedAt(entity.getRecordedAt())
                .build();
    }

    private SnmpInterfaceMetricDto toInterfaceDto(SnmpInterfaceMetric entity) {
        return SnmpInterfaceMetricDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDevice().getId())
                .interfaceIndex(entity.getInterfaceIndex())
                .interfaceName(entity.getInterfaceName())
                .operationalStatus(entity.getOperationalStatus())
                .inboundOctets(entity.getInboundOctets())
                .outboundOctets(entity.getOutboundOctets())
                .recordedAt(entity.getRecordedAt())
                .build();
    }
}
