package com.network.network_monitor.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.MaintenanceScheduleDto;
import com.network.network_monitor.dto.MaintenanceScheduleFormDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.entity.MaintenanceSchedule;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.DeviceLogAction;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.MaintenanceAction;
import com.network.network_monitor.enums.MaintenanceSource;
import com.network.network_monitor.enums.MaintenanceStatus;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.notification.MaintenanceScheduleEvent;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MaintenanceScheduleRepository;
import com.network.network_monitor.repository.UserRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;
import com.network.network_monitor.service.MaintenanceScheduleService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MaintenanceScheduleServiceImpl implements MaintenanceScheduleService {

    private final MaintenanceScheduleRepository maintenanceScheduleRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLogRepository deviceLogRepository;
    private final UserRepository userRepository;
    private final HealthEvaluator healthEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MaintenanceScheduleServiceImpl(
            MaintenanceScheduleRepository maintenanceScheduleRepository,
            DeviceRepository deviceRepository,
            DeviceLogRepository deviceLogRepository,
            UserRepository userRepository,
            HealthEvaluator healthEvaluator,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) Clock clock) {
        this.maintenanceScheduleRepository = maintenanceScheduleRepository;
        this.deviceRepository = deviceRepository;
        this.deviceLogRepository = deviceLogRepository;
        this.userRepository = userRepository;
        this.healthEvaluator = healthEvaluator;
        this.eventPublisher = eventPublisher;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceScheduleDto> getSchedulesByDeviceId(Long deviceId) {
        return maintenanceScheduleRepository.findByDeviceIdOrderByStartsAtDesc(deviceId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceScheduleDto> getUpcomingSchedules(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        int pageSize = Math.max(1, Math.min(limit, 100));
        return maintenanceScheduleRepository.findUpcomingSchedules(now, PageRequest.of(0, pageSize))
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceScheduleDto getScheduleById(Long id) {
        MaintenanceSchedule schedule = maintenanceScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch bảo trì với ID: " + id));
        return mapToDto(schedule);
    }

    @Override
    @Transactional
    public MaintenanceScheduleDto createSchedule(MaintenanceScheduleFormDto formDto, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        validateScheduleTimes(formDto.getStartsAt(), formDto.getEndsAt(), now);

        Device device = deviceRepository.findById(formDto.getDeviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + formDto.getDeviceId()));

        validateOverlap(device.getId(), formDto.getStartsAt(), formDto.getEndsAt(), null);

        User currentUser = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        boolean activateImmediately = !formDto.getStartsAt().isAfter(now);
        MaintenanceStatus initialStatus = activateImmediately ? MaintenanceStatus.ACTIVE : MaintenanceStatus.SCHEDULED;

        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .device(device)
                .title(formDto.getTitle().trim())
                .reason(formDto.getReason() != null ? formDto.getReason().trim() : null)
                .startsAt(formDto.getStartsAt())
                .endsAt(formDto.getEndsAt())
                .status(initialStatus)
                .createdBy(currentUser)
                .activatedAt(activateImmediately ? now : null)
                .build();

        schedule = maintenanceScheduleRepository.save(schedule);

        if (activateImmediately) {
            applyDeviceMaintenance(device, schedule, now);
            publishMaintenanceEvent(schedule, MaintenanceAction.ACTIVATED, now);
        } else {
            publishMaintenanceEvent(schedule, MaintenanceAction.CREATED, now);
        }

        return mapToDto(schedule);
    }

    @Override
    @Transactional
    public MaintenanceScheduleDto updateSchedule(Long id, MaintenanceScheduleFormDto formDto, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        MaintenanceSchedule schedule = maintenanceScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch bảo trì với ID: " + id));

        if (schedule.getStatus() != MaintenanceStatus.SCHEDULED) {
            throw new IllegalStateException("Chỉ có thể chỉnh sửa lịch bảo trì ở trạng thái SCHEDULED");
        }

        validateScheduleTimes(formDto.getStartsAt(), formDto.getEndsAt(), now);
        validateOverlap(schedule.getDevice().getId(), formDto.getStartsAt(), formDto.getEndsAt(), id);

        schedule.setTitle(formDto.getTitle().trim());
        schedule.setReason(formDto.getReason() != null ? formDto.getReason().trim() : null);
        schedule.setStartsAt(formDto.getStartsAt());
        schedule.setEndsAt(formDto.getEndsAt());

        schedule = maintenanceScheduleRepository.save(schedule);
        publishMaintenanceEvent(schedule, MaintenanceAction.UPDATED, now);

        return mapToDto(schedule);
    }

    @Override
    @Transactional
    public void cancelSchedule(Long id, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        MaintenanceSchedule schedule = maintenanceScheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch bảo trì với ID: " + id));

        if (schedule.getStatus() != MaintenanceStatus.SCHEDULED && schedule.getStatus() != MaintenanceStatus.ACTIVE) {
            throw new IllegalStateException("Chỉ có thể hủy lịch bảo trì đang chờ hoặc đang kích hoạt");
        }

        MaintenanceStatus previousStatus = schedule.getStatus();
        schedule.setStatus(MaintenanceStatus.CANCELLED);
        maintenanceScheduleRepository.save(schedule);

        Device device = schedule.getDevice();
        User currentUser = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        Long performedByUserId = currentUser != null ? currentUser.getId() : null;

        if (previousStatus == MaintenanceStatus.ACTIVE) {
            if (device.getStatus() == DeviceStatus.MAINTENANCE && device.getMaintenanceSource() == MaintenanceSource.SCHEDULED) {
                long remainingActive = maintenanceScheduleRepository.countActiveSchedulesExcluding(device.getId(), schedule.getId());
                if (remainingActive == 0) {
                    DeviceStatus prevDeviceStatus = device.getStatus();
                    device.setStatus(DeviceStatus.UNKNOWN);
                    device.setMaintenanceSource(MaintenanceSource.NONE);
                    healthEvaluator.resetCounters(device.getId());
                    deviceRepository.save(device);

                    deviceLogRepository.save(DeviceLog.builder()
                            .device(device)
                            .action(DeviceLogAction.EXIT_MAINTENANCE)
                            .description("Đã hủy lịch bảo trì đang chạy: " + schedule.getTitle())
                            .performedBy(performedByUserId)
                            .createdAt(now)
                            .build());

                    eventPublisher.publishEvent(new DeviceStatusChangedEvent(
                            device.getId(),
                            device.getName(),
                            device.getIpAddress(),
                            prevDeviceStatus,
                            DeviceStatus.UNKNOWN,
                            "Hủy lịch bảo trì đang hoạt động",
                            now
                    ));
                }
            }
        }

        publishMaintenanceEvent(schedule, MaintenanceAction.CANCELLED, now);
    }

    @Override
    @Transactional
    public void processDueSchedules() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<MaintenanceSchedule> dueScheduled = maintenanceScheduleRepository.findDueScheduledSchedules(now);
        for (MaintenanceSchedule schedule : dueScheduled) {
            if (!schedule.getEndsAt().isAfter(now)) {
                schedule.setStatus(MaintenanceStatus.COMPLETED);
                schedule.setCompletedAt(now);
                maintenanceScheduleRepository.save(schedule);
                publishMaintenanceEvent(schedule, MaintenanceAction.COMPLETED, now);
                continue;
            }

            schedule.setStatus(MaintenanceStatus.ACTIVE);
            schedule.setActivatedAt(now);
            maintenanceScheduleRepository.save(schedule);

            Device device = schedule.getDevice();
            applyDeviceMaintenance(device, schedule, now);
            publishMaintenanceEvent(schedule, MaintenanceAction.ACTIVATED, now);
        }

        List<MaintenanceSchedule> dueActive = maintenanceScheduleRepository.findDueActiveSchedules(now);
        for (MaintenanceSchedule schedule : dueActive) {
            schedule.setStatus(MaintenanceStatus.COMPLETED);
            schedule.setCompletedAt(now);
            maintenanceScheduleRepository.save(schedule);

            Device device = schedule.getDevice();
            if (device.getStatus() == DeviceStatus.MAINTENANCE && device.getMaintenanceSource() == MaintenanceSource.SCHEDULED) {
                long remainingActive = maintenanceScheduleRepository.countActiveSchedulesExcluding(device.getId(), schedule.getId());
                if (remainingActive == 0) {
                    DeviceStatus prevStatus = device.getStatus();
                    device.setStatus(DeviceStatus.UNKNOWN);
                    device.setMaintenanceSource(MaintenanceSource.NONE);
                    healthEvaluator.resetCounters(device.getId());
                    deviceRepository.save(device);

                    deviceLogRepository.save(DeviceLog.builder()
                            .device(device)
                            .action(DeviceLogAction.EXIT_MAINTENANCE)
                            .description("Lịch bảo trì kết thúc: " + schedule.getTitle())
                            .createdAt(now)
                            .build());

                    eventPublisher.publishEvent(new DeviceStatusChangedEvent(
                            device.getId(),
                            device.getName(),
                            device.getIpAddress(),
                            prevStatus,
                            DeviceStatus.UNKNOWN,
                            "Hoàn tất lịch bảo trì định kỳ",
                            now
                    ));
                }
            }

            publishMaintenanceEvent(schedule, MaintenanceAction.COMPLETED, now);
        }
    }

    private void applyDeviceMaintenance(Device device, MaintenanceSchedule schedule, LocalDateTime now) {
        if (device.getStatus() != DeviceStatus.MAINTENANCE) {
            DeviceStatus prevStatus = device.getStatus();
            device.setStatus(DeviceStatus.MAINTENANCE);
            device.setMaintenanceSource(MaintenanceSource.SCHEDULED);
            healthEvaluator.resetCounters(device.getId());
            deviceRepository.save(device);

            deviceLogRepository.save(DeviceLog.builder()
                    .device(device)
                    .action(DeviceLogAction.ENTER_MAINTENANCE)
                    .description("Kích hoạt lịch bảo trì: " + schedule.getTitle())
                    .performedBy(schedule.getCreatedBy() != null ? schedule.getCreatedBy().getId() : null)
                    .createdAt(now)
                    .build());

            eventPublisher.publishEvent(new DeviceStatusChangedEvent(
                    device.getId(),
                    device.getName(),
                    device.getIpAddress(),
                    prevStatus,
                    DeviceStatus.MAINTENANCE,
                    "Kích hoạt lịch bảo trì: " + schedule.getTitle(),
                    now
            ));
        } else if (device.getMaintenanceSource() == null || device.getMaintenanceSource() == MaintenanceSource.NONE) {
            device.setMaintenanceSource(MaintenanceSource.SCHEDULED);
            deviceRepository.save(device);
        }
    }

    private void validateScheduleTimes(LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Thời gian bắt đầu và kết thúc không được để trống");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Thời gian bắt đầu phải trước thời gian kết thúc");
        }
        if (!endsAt.isAfter(now)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải ở tương lai");
        }
    }

    private void validateOverlap(Long deviceId, LocalDateTime startsAt, LocalDateTime endsAt, Long excludeId) {
        List<MaintenanceSchedule> overlaps = maintenanceScheduleRepository.findOverlappingSchedules(
                deviceId, startsAt, endsAt, excludeId,
                Set.of(MaintenanceStatus.SCHEDULED, MaintenanceStatus.ACTIVE));
        if (!overlaps.isEmpty()) {
            MaintenanceSchedule existing = overlaps.get(0);
            throw new IllegalArgumentException("Khoảng thời gian bảo trì trùng với lịch '" + existing.getTitle() + "' ("
                    + existing.getStartsAt() + " - " + existing.getEndsAt() + ")");
        }
    }

    private void publishMaintenanceEvent(MaintenanceSchedule schedule, MaintenanceAction action, LocalDateTime now) {
        MaintenanceScheduleEvent event = new MaintenanceScheduleEvent(
                schedule.getId(),
                schedule.getDevice().getId(),
                schedule.getDevice().getName(),
                schedule.getTitle(),
                action,
                schedule.getStatus(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                now
        );
        eventPublisher.publishEvent(event);
    }

    private MaintenanceScheduleDto mapToDto(MaintenanceSchedule s) {
        return MaintenanceScheduleDto.builder()
                .id(s.getId())
                .deviceId(s.getDevice() != null ? s.getDevice().getId() : null)
                .deviceName(s.getDevice() != null ? s.getDevice().getName() : null)
                .ipAddress(s.getDevice() != null ? s.getDevice().getIpAddress() : null)
                .title(s.getTitle())
                .reason(s.getReason())
                .startsAt(s.getStartsAt())
                .endsAt(s.getEndsAt())
                .status(s.getStatus())
                .createdByName(s.getCreatedBy() != null ? s.getCreatedBy().getUsername() : null)
                .createdAt(s.getCreatedAt())
                .activatedAt(s.getActivatedAt())
                .completedAt(s.getCompletedAt())
                .build();
    }
}
