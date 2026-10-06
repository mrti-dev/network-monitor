package com.network.network_monitor.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.notification.AlertEvent;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TcpPortAlertWorkflowTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock fixedClock;
    private AlertServiceImpl alertService;
    private Device testDevice;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneId.of("UTC"));
        alertService = new AlertServiceImpl(alertRepository, userRepository, eventPublisher, fixedClock);

        testDevice = Device.builder()
                .id(1L)
                .name("Core Switch")
                .ipAddress("192.168.1.1")
                .build();
    }

    @Test
    void triggerPortAlert_createsSingleAlert_andDoesNotDuplicate() {
        when(alertRepository.findByDeviceIdAndAlertTypeAndPortAndStatusIn(
                eq(1L), eq(AlertType.PORT_DOWN), eq(80), any()))
                .thenReturn(List.of());

        alertService.triggerPortAlert(testDevice, 80, "HTTP", "Connection refused");

        verify(alertRepository, times(1)).save(argThat(alert ->
                alert.getPort().equals(80)
                && alert.getAlertType() == AlertType.PORT_DOWN
                && alert.getStatus() == AlertStatus.TRIGGERED
                && alert.getSeverity() == AlertSeverity.CRITICAL
                && alert.getMessage().contains("HTTP")
                && alert.getMessage().contains("80")
        ));
        verify(eventPublisher, times(1)).publishEvent(any(AlertEvent.class));

        // Khi alert đã tồn tại (activeAlerts không rỗng) -> không tạo thêm alert
        Alert existingAlert = Alert.builder().id(99L).port(80).alertType(AlertType.PORT_DOWN).status(AlertStatus.TRIGGERED).build();
        when(alertRepository.findByDeviceIdAndAlertTypeAndPortAndStatusIn(
                eq(1L), eq(AlertType.PORT_DOWN), eq(80), any()))
                .thenReturn(List.of(existingAlert));

        alertService.triggerPortAlert(testDevice, 80, "HTTP", "Connection refused");
        // Số lần save không tăng
        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void resolvePortAlert_resolvesOnlyTargetPortAlert() {
        Alert alertPort80 = Alert.builder()
                .id(101L)
                .device(testDevice)
                .port(80)
                .alertType(AlertType.PORT_DOWN)
                .status(AlertStatus.TRIGGERED)
                .triggeredAt(LocalDateTime.now(fixedClock).minusMinutes(5))
                .build();

        when(alertRepository.findByDeviceIdAndAlertTypeAndPortAndStatusIn(
                eq(1L), eq(AlertType.PORT_DOWN), eq(80), any()))
                .thenReturn(List.of(alertPort80));

        alertService.resolvePortAlert(testDevice, 80);

        assertThat(alertPort80.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alertPort80.getResolvedAt()).isNotNull();
        verify(alertRepository, times(1)).save(alertPort80);
        verify(eventPublisher, times(1)).publishEvent(argThat((AlertEvent e) ->
                e.alertId().equals(101L) && "RESOLVED".equals(e.action())));
    }

    private Long eq(Long val) {
        return org.mockito.ArgumentMatchers.eq(val);
    }

    private Integer eq(Integer val) {
        return org.mockito.ArgumentMatchers.eq(val);
    }

    private AlertType eq(AlertType val) {
        return org.mockito.ArgumentMatchers.eq(val);
    }
}
