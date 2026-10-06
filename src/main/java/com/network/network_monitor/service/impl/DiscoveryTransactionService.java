package com.network.network_monitor.service.impl;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.DiscoveryOutcome;
import com.network.network_monitor.dto.ScanResponseDto.DiscoveredDevice;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceLogAction;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscoveryTransactionService {

    private final DeviceRepository deviceRepository;
    private final DeviceLogRepository deviceLogRepository;
    private final HealthEvaluator healthEvaluator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DiscoveryOutcome processDiscoveredDevice(DiscoveredDevice dev, String targetSubnet) {
        Optional<Device> existingOpt = deviceRepository.findByIpAddressIncludingDeleted(dev.getIpAddress());
        if (existingOpt.isEmpty()) {
            enrollDevice(dev, targetSubnet);
            return DiscoveryOutcome.ADDED;
        }
        Device existing = existingOpt.get();
        if (!existing.getIsDeleted()) {
            if (existing.getDeviceType() == null && dev.getDeviceType() != null) {
                existing.setDeviceType(dev.getDeviceType());
                deviceRepository.save(existing);
            }
            return DiscoveryOutcome.SKIPPED;
        }
        existing.setIsDeleted(false);
        existing.setStatus(DeviceStatus.UNKNOWN);
        if (dev.getMacAddress() != null && !dev.getMacAddress().isEmpty()) {
            existing.setMacAddress(dev.getMacAddress());
        }
        if (dev.getDeviceType() != null) {
            existing.setDeviceType(dev.getDeviceType());
        }
        existing.setIsMonitored(true);
        deviceRepository.saveAndFlush(existing);
        if (existing.getMonitoringConfig() == null) {
            existing.setMonitoringConfig(MonitoringConfig.builder()
                    .device(existing)
                    .strategyType(ProbingMethod.ICMP)
                    .build());
        }
        deviceRepository.save(existing);
        healthEvaluator.resetCounters(existing.getId());
        deviceLogRepository.save(DeviceLog.builder()
                .device(existing)
                .action(DeviceLogAction.REACTIVATE)
                .description("Reactivated via discovery sweep " + targetSubnet)
                .createdAt(LocalDateTime.now())
                .build());
        return DiscoveryOutcome.REACTIVATED;
    }
    private void enrollDevice(DiscoveredDevice dev, String subnet) {
        Device device = Device.builder()
                .name("Auto-Discovered: " + dev.getIpAddress())
                .ipAddress(dev.getIpAddress())
                .macAddress(dev.getMacAddress())
                .deviceType(dev.getDeviceType())
                .status(DeviceStatus.UNKNOWN)
                .isMonitored(true)
                .isDeleted(false)
                .build();

        MonitoringConfig config = MonitoringConfig.builder()
                .device(device)
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
}
