package com.network.network_monitor.service.impl;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.DeviceFilterDto;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.dto.MonitoringUpdateDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceGroup;
import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceLogAction;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.MaintenanceSource;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.repository.DeviceGroupRepository;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;
import com.network.network_monitor.service.DeviceService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "name", "ipAddress", "macAddress", "status", "deviceType", "location", "isMonitored", "createdAt", "updatedAt"
    );

    private final DeviceRepository deviceRepository;
    private final DeviceGroupRepository groupRepository;
    private final HealthEvaluator healthEvaluator;
    private final MonitoringDefaults defaults;
    private final ApplicationEventPublisher events;

    private final MetricLogRepository metricLogRepository;
    private final DeviceLogRepository deviceLogRepository;

    @Autowired
    public DeviceServiceImpl(DeviceRepository deviceRepository,
                             DeviceGroupRepository groupRepository,
                             HealthEvaluator healthEvaluator,
                             MonitoringDefaults defaults,
                             ApplicationEventPublisher events,
                             MetricLogRepository metricLogRepository,
                             DeviceLogRepository deviceLogRepository) {
        this.deviceRepository = deviceRepository;
        this.groupRepository = groupRepository;
        this.healthEvaluator = healthEvaluator;
        this.defaults = defaults;
        this.events = events;
        this.metricLogRepository = metricLogRepository;
        this.deviceLogRepository = deviceLogRepository;
    }

    public DeviceServiceImpl(DeviceRepository deviceRepository,
                             HealthEvaluator healthEvaluator,
                             MonitoringDefaults defaults,
                             MetricLogRepository metricLogRepository,
                             DeviceLogRepository deviceLogRepository) {
        this(deviceRepository, null, healthEvaluator, defaults, null, metricLogRepository, deviceLogRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceResponseDto> getAllDevices(Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        return deviceRepository.findAll(sanitized)
                .map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceResponseDto> searchDevices(DeviceFilterDto filter, Pageable pageable) {
        Pageable sanitized = sanitizePageable(pageable);
        if (filter == null) {
            return deviceRepository.findAll(sanitized).map(this::mapToResponseDto);
        }

        String keyword = (filter.getKeyword() != null && !filter.getKeyword().isBlank())
                ? filter.getKeyword().trim() : null;
        String location = (filter.getLocation() != null && !filter.getLocation().isBlank())
                ? filter.getLocation().trim() : null;

        return deviceRepository.findFilteredDevices(
                filter.getStatus(),
                filter.getDeviceType(),
                filter.getMonitored(),
                filter.getGroupId(),
                location,
                keyword,
                sanitized
        ).map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Double getLatencyThreshold(Long deviceId) {
        var projection = deviceRepository.findLatencyThreshold(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + deviceId));
        return projection.getLatencyThreshold() != null ? projection.getLatencyThreshold() : defaults.getLatencyThreshold();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceFormDto getDeviceFormById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        return mapToFormDto(device);
    }

    @Override
    @Transactional
    public void saveDevice(DeviceFormDto formDto) {
        DeviceGroup group = null;
        if (formDto.getGroupId() != null) {
            group = groupRepository.findById(formDto.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm thiết bị với ID: " + formDto.getGroupId()));
        }

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
                    existingDevice.setGroup(group);
                    existingDevice.setIsMonitored(true);

                    if (existingDevice.getMonitoringConfig() == null) {
                        existingDevice.setMonitoringConfig(MonitoringConfig.builder()
                                .device(existingDevice)
                                .strategyType(ProbingMethod.ICMP)
                                .build());
                    }
                    applyPresentMonitoringOverrides(existingDevice.getMonitoringConfig(), formDto);

                    healthEvaluator.resetCounters(existingDevice.getId());
                    try {
                        deviceRepository.saveAndFlush(existingDevice);
                    } catch (CannotAcquireLockException | DataIntegrityViolationException ex) {
                        throw new OptimisticLockingFailureException(
                                "Thiết bị đã được khôi phục bởi một yêu cầu khác", ex);
                    }

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
                    .group(group)
                    .status(DeviceStatus.UNKNOWN)
                    .isMonitored(true)
                    .isDeleted(false)
                    .build();

            MonitoringConfig config = MonitoringConfig.builder()
                    .device(device)
                    .strategyType(ProbingMethod.ICMP)
                    .build();
            applyMonitoringConfig(config, formDto);
            device.setMonitoringConfig(config);

            deviceRepository.save(device);
            events.publishEvent(new DeviceStatusChangedEvent(
                    device.getId(),
                    device.getName(),
                    device.getIpAddress(),
                    null,
                    device.getStatus(),
                    "Khởi tạo thiết bị mới",
                    LocalDateTime.now()
            ));
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
            device.setGroup(group);

            if (device.getMonitoringConfig() == null) {
                device.setMonitoringConfig(MonitoringConfig.builder()
                        .device(device)
                        .strategyType(ProbingMethod.ICMP)
                        .build());
            }
            applyMonitoringConfig(device.getMonitoringConfig(), formDto);

            deviceRepository.save(device);
        }
    }

    @Override
    @Transactional
    public void updateMonitoringConfig(MonitoringUpdateDto updateDto) {
        Device device = deviceRepository.findById(updateDto.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + updateDto.getDeviceId()));

        DeviceStatus previousStatus = device.getStatus();

        if (updateDto.getGroupId() != null) {
            DeviceGroup group = groupRepository.findById(updateDto.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm thiết bị với ID: " + updateDto.getGroupId()));
            device.setGroup(group);
        }

        if (updateDto.getIsMonitored() != null) {
            device.setIsMonitored(updateDto.getIsMonitored());
        }

        if (updateDto.getMaintenanceMode() != null) {
            if (updateDto.getMaintenanceMode() && device.getStatus() != DeviceStatus.MAINTENANCE) {
                healthEvaluator.resetCounters(device.getId());
                device.setStatus(DeviceStatus.MAINTENANCE);
                device.setMaintenanceSource(MaintenanceSource.MANUAL);
            } else if (!updateDto.getMaintenanceMode() && device.getStatus() == DeviceStatus.MAINTENANCE) {
                healthEvaluator.resetCounters(device.getId());
                device.setStatus(DeviceStatus.UNKNOWN);
                device.setMaintenanceSource(MaintenanceSource.NONE);
            }
        }

        if (device.getMonitoringConfig() == null) {
            device.setMonitoringConfig(MonitoringConfig.builder()
                    .device(device)
                    .strategyType(ProbingMethod.ICMP)
                    .build());
        }

        MonitoringConfig config = device.getMonitoringConfig();
        if (Boolean.TRUE.equals(updateDto.getUseGroupConfig())) {
            config.setPingInterval(null);
            config.setTimeoutMs(null);
            config.setLatencyThreshold(null);
        } else {
            if (updateDto.getPingInterval() != null) {
                config.setPingInterval(updateDto.getPingInterval());
            }
            if (updateDto.getTimeoutMs() != null) {
                config.setTimeoutMs(updateDto.getTimeoutMs());
            }
            if (updateDto.getLatencyThreshold() != null) {
                config.setLatencyThreshold(updateDto.getLatencyThreshold());
            }
        }

        deviceRepository.save(device);

        if (device.getStatus() != previousStatus) {
            events.publishEvent(new DeviceStatusChangedEvent(
                    device.getId(),
                    device.getName(),
                    device.getIpAddress(),
                    previousStatus,
                    device.getStatus(),
                    "Cập nhật cấu hình giám sát",
                    LocalDateTime.now()
            ));
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

        events.publishEvent(new DeviceStatusChangedEvent(
                device.getId(),
                device.getName(),
                device.getIpAddress(),
                device.getStatus(),
                device.getStatus(),
                device.getIsMonitored() ? "Bật giám sát" : "Tắt giám sát",
                LocalDateTime.now()
        ));
    }

    @Override
    @Transactional
    public void toggleMaintenanceMode(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));

        DeviceStatus previousStatus = device.getStatus();
        healthEvaluator.resetCounters(id);

        if (device.getStatus() == DeviceStatus.MAINTENANCE) {
            device.setStatus(DeviceStatus.UNKNOWN);
            device.setMaintenanceSource(MaintenanceSource.NONE);
        } else {
            device.setStatus(DeviceStatus.MAINTENANCE);
            device.setMaintenanceSource(MaintenanceSource.MANUAL);
        }

        deviceRepository.save(device);

        events.publishEvent(new DeviceStatusChangedEvent(
                device.getId(),
                device.getName(),
                device.getIpAddress(),
                previousStatus,
                device.getStatus(),
                device.getStatus() == DeviceStatus.MAINTENANCE ? "Kích hoạt bảo trì (MAINTENANCE)" : "Thoát chế độ bảo trì",
                LocalDateTime.now()
        ));
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        }
        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> validOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                validOrders.add(order);
            }
        }

        if (validOrders.isEmpty()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(validOrders));
    }

    private DeviceResponseDto mapToResponseDto(Device device) {
        Integer pingInterval = configValue(device, MonitoringConfig::getPingInterval,
                device.getGroup() != null && device.getGroup().getPingInterval() != null ? device.getGroup().getPingInterval() : defaults.getPingInterval());
        Integer timeoutMs = configValue(device, MonitoringConfig::getTimeoutMs,
                device.getGroup() != null && device.getGroup().getTimeoutMs() != null ? device.getGroup().getTimeoutMs() : defaults.getTimeoutMs());
        Double latencyThreshold = configValue(device, MonitoringConfig::getLatencyThreshold,
                device.getGroup() != null && device.getGroup().getLatencyThreshold() != null ? device.getGroup().getLatencyThreshold() : defaults.getLatencyThreshold());

        return DeviceResponseDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .status(device.getStatus())
                .location(device.getLocation())
                .isMonitored(device.getIsMonitored())
                .groupId(device.getGroup() != null ? device.getGroup().getId() : null)
                .groupName(device.getGroup() != null ? device.getGroup().getName() : null)
                .pingInterval(pingInterval)
                .timeoutMs(timeoutMs)
                .latencyThreshold(latencyThreshold)
                .maintenanceSource(device.getMaintenanceSource() != null ? device.getMaintenanceSource() : MaintenanceSource.NONE)
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    private DeviceFormDto mapToFormDto(Device device) {
        MonitoringConfig config = device.getMonitoringConfig();
        return DeviceFormDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .location(device.getLocation())
                .groupId(device.getGroup() != null ? device.getGroup().getId() : null)
                .pingInterval(config != null ? config.getPingInterval() : null)
                .timeoutMs(config != null ? config.getTimeoutMs() : null)
                .latencyThreshold(config != null ? config.getLatencyThreshold() : null)
                .build();
    }

    private void applyMonitoringConfig(MonitoringConfig config, DeviceFormDto form) {
        config.setPingInterval(form.getPingInterval());
        config.setTimeoutMs(form.getTimeoutMs());
        config.setLatencyThreshold(form.getLatencyThreshold());
    }

    private void applyPresentMonitoringOverrides(MonitoringConfig config, DeviceFormDto form) {
        if (form.getPingInterval() != null) config.setPingInterval(form.getPingInterval());
        if (form.getTimeoutMs() != null) config.setTimeoutMs(form.getTimeoutMs());
        if (form.getLatencyThreshold() != null) config.setLatencyThreshold(form.getLatencyThreshold());
    }

    private <T> T configValue(Device device, java.util.function.Function<MonitoringConfig, T> getter, T fallback) {
        return device.getMonitoringConfig() == null ? fallback : Optional.ofNullable(getter.apply(device.getMonitoringConfig())).orElse(fallback);
    }
}
