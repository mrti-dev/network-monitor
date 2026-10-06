package com.network.network_monitor.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.notification.MaintenanceScheduleEvent;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MaintenanceScheduleRepository;
import com.network.network_monitor.repository.UserRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;

@ExtendWith(MockitoExtension.class)
class MaintenanceScheduleServiceTest {

    @Mock
    private MaintenanceScheduleRepository scheduleRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceLogRepository deviceLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HealthEvaluator healthEvaluator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock fixedClock;
    private LocalDateTime baseTime;
    private MaintenanceScheduleServiceImpl service;

    private Device testDevice;

    @BeforeEach
    void setUp() {
        baseTime = LocalDateTime.of(2026, 10, 6, 10, 0, 0);
        Instant instant = baseTime.atZone(ZoneId.systemDefault()).toInstant();
        fixedClock = Clock.fixed(instant, ZoneId.systemDefault());

        service = new MaintenanceScheduleServiceImpl(
                scheduleRepository,
                deviceRepository,
                deviceLogRepository,
                userRepository,
                healthEvaluator,
                eventPublisher,
                fixedClock
        );

        testDevice = Device.builder()
                .id(1L)
                .name("Core Switch")
                .ipAddress("192.168.1.1")
                .status(DeviceStatus.ONLINE)
                .maintenanceSource(MaintenanceSource.NONE)
                .isMonitored(true)
                .build();
    }

    @Test
    @DisplayName("Validation startsAt và endsAt: ném ngoại lệ khi thời gian không hợp lệ")
    void testValidationStartsAtEndsAt() {
        MaintenanceScheduleFormDto nullDto = MaintenanceScheduleFormDto.builder()
                .deviceId(1L)
                .title("Bảo trì")
                .startsAt(null)
                .endsAt(baseTime.plusHours(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(nullDto, "admin"));

        MaintenanceScheduleFormDto reversedDto = MaintenanceScheduleFormDto.builder()
                .deviceId(1L)
                .title("Bảo trì")
                .startsAt(baseTime.plusHours(2))
                .endsAt(baseTime.plusHours(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(reversedDto, "admin"));

        MaintenanceScheduleFormDto pastDto = MaintenanceScheduleFormDto.builder()
                .deviceId(1L)
                .title("Bảo trì")
                .startsAt(baseTime.minusHours(3))
                .endsAt(baseTime.minusHours(1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> service.createSchedule(pastDto, "admin"));
    }

    @Test
    @DisplayName("Phát hiện lịch chồng nhau: ném ngoại lệ khi có lịch SCHEDULED hoặc ACTIVE trùng")
    void testDetectOverlappingSchedules() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));

        MaintenanceSchedule existing = MaintenanceSchedule.builder()
                .id(100L)
                .device(testDevice)
                .title("Bảo trì định kỳ cũ")
                .startsAt(baseTime.plusHours(1))
                .endsAt(baseTime.plusHours(3))
                .status(MaintenanceStatus.SCHEDULED)
                .build();

        when(scheduleRepository.findOverlappingSchedules(eq(1L), any(), any(), isNull(), any()))
                .thenReturn(List.of(existing));

        MaintenanceScheduleFormDto formDto = MaintenanceScheduleFormDto.builder()
                .deviceId(1L)
                .title("Bảo trì mới trùng giờ")
                .startsAt(baseTime.plusHours(2))
                .endsAt(baseTime.plusHours(4))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createSchedule(formDto, "admin"));
        assertTrue(ex.getMessage().contains("trùng với lịch"));
    }

    @Test
    @DisplayName("Kích hoạt đúng thời điểm: lịch đến hạn chuyển ACTIVE, device chuyển MAINTENANCE (SCHEDULED)")
    void testActivateDueSchedule() {
        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(10L)
                .device(testDevice)
                .title("Nâng cấp Switch")
                .startsAt(baseTime.minusMinutes(1))
                .endsAt(baseTime.plusHours(1))
                .status(MaintenanceStatus.SCHEDULED)
                .build();

        when(scheduleRepository.findDueScheduledSchedules(baseTime)).thenReturn(List.of(schedule));
        when(scheduleRepository.findDueActiveSchedules(baseTime)).thenReturn(Collections.emptyList());

        service.processDueSchedules();

        assertEquals(MaintenanceStatus.ACTIVE, schedule.getStatus());
        assertEquals(baseTime, schedule.getActivatedAt());
        assertEquals(DeviceStatus.MAINTENANCE, testDevice.getStatus());
        assertEquals(MaintenanceSource.SCHEDULED, testDevice.getMaintenanceSource());

        verify(healthEvaluator).resetCounters(1L);
        verify(deviceRepository).save(testDevice);
        verify(scheduleRepository).save(schedule);

        ArgumentCaptor<MaintenanceScheduleEvent> eventCaptor = ArgumentCaptor.forClass(MaintenanceScheduleEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(MaintenanceAction.ACTIVATED, eventCaptor.getValue().action());
    }

    @Test
    @DisplayName("Kết thúc lịch đúng thời điểm: chuyển COMPLETED, device trở về UNKNOWN")
    void testCompleteDueSchedule() {
        testDevice.setStatus(DeviceStatus.MAINTENANCE);
        testDevice.setMaintenanceSource(MaintenanceSource.SCHEDULED);

        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(10L)
                .device(testDevice)
                .title("Nâng cấp Switch")
                .startsAt(baseTime.minusHours(2))
                .endsAt(baseTime)
                .status(MaintenanceStatus.ACTIVE)
                .build();

        when(scheduleRepository.findDueScheduledSchedules(baseTime)).thenReturn(Collections.emptyList());
        when(scheduleRepository.findDueActiveSchedules(baseTime)).thenReturn(List.of(schedule));
        when(scheduleRepository.countActiveSchedulesExcluding(1L, 10L)).thenReturn(0L);

        service.processDueSchedules();

        assertEquals(MaintenanceStatus.COMPLETED, schedule.getStatus());
        assertEquals(baseTime, schedule.getCompletedAt());
        assertEquals(DeviceStatus.UNKNOWN, testDevice.getStatus());
        assertEquals(MaintenanceSource.NONE, testDevice.getMaintenanceSource());

        verify(healthEvaluator).resetCounters(1L);
        verify(deviceRepository).save(testDevice);
    }

    @Test
    @DisplayName("Không tắt maintenance thủ công: nếu device là MANUAL, khi lịch COMPLETED vẫn giữ MAINTENANCE")
    void testDoNotTurnOffManualMaintenance() {
        testDevice.setStatus(DeviceStatus.MAINTENANCE);
        testDevice.setMaintenanceSource(MaintenanceSource.MANUAL);

        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(10L)
                .device(testDevice)
                .title("Lịch hết hạn nhưng có manual maintenance")
                .startsAt(baseTime.minusHours(2))
                .endsAt(baseTime)
                .status(MaintenanceStatus.ACTIVE)
                .build();

        when(scheduleRepository.findDueScheduledSchedules(baseTime)).thenReturn(Collections.emptyList());
        when(scheduleRepository.findDueActiveSchedules(baseTime)).thenReturn(List.of(schedule));

        service.processDueSchedules();

        assertEquals(MaintenanceStatus.COMPLETED, schedule.getStatus());
        // Thiết bị phải giữ nguyên MANUAL và MAINTENANCE
        assertEquals(DeviceStatus.MAINTENANCE, testDevice.getStatus());
        assertEquals(MaintenanceSource.MANUAL, testDevice.getMaintenanceSource());
        verify(deviceRepository, never()).save(testDevice);
    }

    @Test
    @DisplayName("Job chạy lại vẫn idempotent: không có schedule nào due thì không thay đổi gì")
    void testIdempotentExecution() {
        when(scheduleRepository.findDueScheduledSchedules(baseTime)).thenReturn(Collections.emptyList());
        when(scheduleRepository.findDueActiveSchedules(baseTime)).thenReturn(Collections.emptyList());

        service.processDueSchedules();

        verify(deviceRepository, never()).save(any());
        verify(scheduleRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Hủy lịch bảo trì đang ACTIVE: device SCHEDULED chuyển về UNKNOWN")
    void testCancelActiveSchedule() {
        testDevice.setStatus(DeviceStatus.MAINTENANCE);
        testDevice.setMaintenanceSource(MaintenanceSource.SCHEDULED);

        MaintenanceSchedule schedule = MaintenanceSchedule.builder()
                .id(15L)
                .device(testDevice)
                .title("Lịch bảo trì đột xuất")
                .status(MaintenanceStatus.ACTIVE)
                .startsAt(baseTime.minusHours(1))
                .endsAt(baseTime.plusHours(1))
                .build();

        when(scheduleRepository.findById(15L)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.countActiveSchedulesExcluding(1L, 15L)).thenReturn(0L);

        service.cancelSchedule(15L, "admin");

        assertEquals(MaintenanceStatus.CANCELLED, schedule.getStatus());
        assertEquals(DeviceStatus.UNKNOWN, testDevice.getStatus());
        assertEquals(MaintenanceSource.NONE, testDevice.getMaintenanceSource());

        verify(deviceRepository).save(testDevice);
        verify(scheduleRepository).save(schedule);
    }
}
