package com.network.network_monitor.service.impl;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceLogAction;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;
import com.network.network_monitor.service.DeviceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final HealthEvaluator healthEvaluator;
    private final MonitoringDefaults defaults;

    private final MetricLogRepository metricLogRepository;
    private final DeviceLogRepository deviceLogRepository;

    @Override
    public Page<DeviceResponseDto> getAllDevices(Pageable pageable) {
        return deviceRepository.findAll(pageable)
                .map(this::mapToResponseDto);
    }

    @Override
    public List<MetricLogDto> getDeviceMetrics(Long deviceId) {
        return metricLogRepository.findTop30ByDeviceIdOrderByRecordedAtDescIdDesc(deviceId).stream()
                .map(m -> MetricLogDto.builder()
                        .recordedAt(m.getRecordedAt())
                        .latencyMs(m.getLatencyMs())
                        .packetLoss(m.getPacketLoss())
                        .isReachable(m.getIsReachable())
                        .build())
                .toList();
    }

    @Override
    public Double getLatencyThreshold(Long deviceId) {
        var projection = deviceRepository.findLatencyThreshold(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + deviceId));
        return projection.getLatencyThreshold() != null ? projection.getLatencyThreshold() : defaults.getLatencyThreshold();
    }

    @Override
    public DeviceFormDto getDeviceFormById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        return mapToFormDto(device);
    }

    @Override
    @Transactional
    public void saveDevice(DeviceFormDto formDto) {
        if (formDto.getId() == null) {
            Optional<Device> existingDeviceOpt = deviceRepository.findByIpAddressIncludingDeleted(formDto.getIpAddress());
            if (existingDeviceOpt.isPresent()) {
                Device existingDevice = existingDeviceOpt.get();
                if (existingDevice.getIsDeleted()) {

                    existingDevice.setIsDeleted(false);
                    existingDevice.setStatus(DeviceStatus.UNKNOWN);
                    existingDevice.setName(formDto.getName());
                    existingDevice.setMacAddress(formDto.getMacAddress());
                    existingDevice.setDeviceType(formDto.getDeviceType());
                    existingDevice.setLocation(formDto.getLocation());
                    existingDevice.setIsMonitored(true);
                    deviceRepository.saveAndFlush(existingDevice);

                    if (existingDevice.getMonitoringConfig() == null) {
                        MonitoringConfig config = MonitoringConfig.builder()
                                .device(existingDevice)
                                .pingInterval(defaults.getPingInterval())
                                .timeoutMs(defaults.getTimeoutMs())
                                .latencyThreshold(defaults.getLatencyThreshold())
                                .strategyType(ProbingMethod.ICMP)
                                .build();
                        existingDevice.setMonitoringConfig(config);
                    }

                    healthEvaluator.resetCounters(existingDevice.getId());
                    deviceRepository.save(existingDevice);

                    DeviceLog logEntry = DeviceLog.builder()
                            .device(existingDevice)
                            .action(DeviceLogAction.REACTIVATE)
                            .description("Reactivated device with IP " + formDto.getIpAddress())
                            .createdAt(LocalDateTime.now())
                            .build();
                    deviceLogRepository.save(logEntry);
                    return;

                } else {
                    throw new DuplicateResourceException("IP " + formDto.getIpAddress() + " đã tồn tại trong hệ thống.");
                }
            }
            Device device = Device.builder()
                    .name(formDto.getName())
                    .ipAddress(formDto.getIpAddress())
                    .macAddress(formDto.getMacAddress())
                    .deviceType(formDto.getDeviceType())
                    .location(formDto.getLocation())
                    .status(DeviceStatus.UNKNOWN)
                    .isMonitored(true)
                    .isDeleted(false)
                    .build();

            MonitoringConfig config = MonitoringConfig.builder()
                    .device(device)
                    .pingInterval(defaults.getPingInterval())
                    .timeoutMs(defaults.getTimeoutMs())
                    .latencyThreshold(defaults.getLatencyThreshold())
                    .strategyType(ProbingMethod.ICMP)
                    .build();
            device.setMonitoringConfig(config);

            deviceRepository.save(device);
        } else {
            Device device = deviceRepository.findById(formDto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + formDto.getId()));

            device.setName(formDto.getName());
            if (!device.getIpAddress().equals(formDto.getIpAddress())) {
                Optional<Device> otherDevice = deviceRepository.findByIpAddressIncludingDeleted(formDto.getIpAddress());
                if (otherDevice.isPresent()) {
                    throw new DuplicateResourceException("IP " + formDto.getIpAddress() + " đã tồn tại trong hệ thống (có thể thuộc về một thiết bị đã bị xóa).");
                }
                healthEvaluator.resetCounters(device.getId());
                device.setIpAddress(formDto.getIpAddress());
            }
            device.setMacAddress(formDto.getMacAddress());
            device.setDeviceType(formDto.getDeviceType());
            device.setLocation(formDto.getLocation());

            deviceRepository.save(device);
        }
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        healthEvaluator.resetCounters(id);

        device.setIsDeleted(true);
        deviceRepository.flush();

        DeviceLog logEntry = DeviceLog.builder()
                .device(device)
                .action(DeviceLogAction.SOFT_DELETE)
                .description("Soft deleted device")
                .createdAt(LocalDateTime.now())
                .build();
        deviceLogRepository.save(logEntry);
    }

    @Override
    @Transactional
    public void toggleMonitoring(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        device.setIsMonitored(!device.getIsMonitored());
        healthEvaluator.resetCounters(id);
        deviceRepository.save(device);
    }

    private DeviceResponseDto mapToResponseDto(Device device) {
        return DeviceResponseDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .status(device.getStatus())
                .location(device.getLocation())
                .isMonitored(device.getIsMonitored())
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    private DeviceFormDto mapToFormDto(Device device) {
        return DeviceFormDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .location(device.getLocation())
                .build();
    }
}
