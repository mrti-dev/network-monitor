package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.AlertFilterDto;
import com.network.network_monitor.dto.AlertProjection;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceWorkflowTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlertServiceImpl alertService;

    private Device sampleDevice;
    private Alert sampleAlert;

    @BeforeEach
    void setUp() {
        alertService = new AlertServiceImpl(alertRepository, userRepository, eventPublisher);

        sampleDevice = Device.builder()
                .id(1L)
                .name("Core Switch")
                .ipAddress("192.168.1.1")
                .macAddress("00:11:22:33:44:55")
                .deviceType(DeviceType.SWITCH)
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build();

        sampleAlert = Alert.builder()
                .id(100L)
                .device(sampleDevice)
                .alertType(AlertType.OFFLINE)
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.TRIGGERED)
                .message("Device went offline")
                .triggeredAt(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    @Test
    void acknowledgeAlert_fromTriggered_success() {
        User operator = User.builder().id(1L).username("operator_user").build();
        when(userRepository.findByUsername("operator_user")).thenReturn(Optional.of(operator));
        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.acknowledgeAlert(100L, "operator_user");

        assertThat(sampleAlert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(sampleAlert.getAcknowledgedBy()).isEqualTo(operator);
        assertThat(sampleAlert.getAcknowledgedAt()).isNotNull();

        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(alertRepository).save(sampleAlert);
    }

    @Test
    void acknowledgeAlert_alreadyAcknowledged_idempotent() {
        sampleAlert.setStatus(AlertStatus.ACKNOWLEDGED);
        User firstUser = User.builder().id(2L).username("first_user").build();
        sampleAlert.setAcknowledgedBy(firstUser);
        LocalDateTime firstAckAt = LocalDateTime.now().minusMinutes(5);
        sampleAlert.setAcknowledgedAt(firstAckAt);

        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));

        alertService.acknowledgeAlert(100L, "new_operator");

        assertThat(sampleAlert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(sampleAlert.getAcknowledgedBy()).isEqualTo(firstUser);
        assertThat(sampleAlert.getAcknowledgedAt()).isEqualTo(firstAckAt);
        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void acknowledgeAlert_alreadyResolved_throwsIllegalState() {
        sampleAlert.setStatus(AlertStatus.RESOLVED);
        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(100L, "operator_user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESOLVED");
    }

    @Test
    void resolveAlert_fromTriggered_success() {
        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.resolveAlert(100L);

        assertThat(sampleAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(sampleAlert.getResolvedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(alertRepository).save(sampleAlert);
    }

    @Test
    void resolveAlert_fromAcknowledged_success() {
        sampleAlert.setStatus(AlertStatus.ACKNOWLEDGED);
        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.resolveAlert(100L);

        assertThat(sampleAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(sampleAlert.getResolvedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void resolveAlert_alreadyResolved_idempotent() {
        sampleAlert.setStatus(AlertStatus.RESOLVED);
        LocalDateTime initialResolved = LocalDateTime.now().minusMinutes(2);
        sampleAlert.setResolvedAt(initialResolved);

        when(alertRepository.findById(100L)).thenReturn(Optional.of(sampleAlert));

        alertService.resolveAlert(100L);

        assertThat(sampleAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(sampleAlert.getResolvedAt()).isEqualTo(initialResolved);
        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void alertNotFound_throwsResourceNotFoundException() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledgeAlert(999L, "operator"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchAlerts_canReadAlertsOfSoftDeletedDevice() {
        AlertProjection mockProjection = mock(AlertProjection.class);
        when(mockProjection.getId()).thenReturn(100L);
        when(mockProjection.getDeviceId()).thenReturn(1L);
        when(mockProjection.getDeviceName()).thenReturn("Core Switch");
        when(mockProjection.getIsDeviceDeleted()).thenReturn(true);
        when(mockProjection.getAlertType()).thenReturn("OFFLINE");
        when(mockProjection.getMessage()).thenReturn("Device went offline");
        when(mockProjection.getSeverity()).thenReturn("CRITICAL");
        when(mockProjection.getStatus()).thenReturn("TRIGGERED");
        when(mockProjection.getTriggeredAt()).thenReturn(LocalDateTime.now());

        AlertFilterDto filter = new AlertFilterDto();
        Pageable pageable = PageRequest.of(0, 10);

        when(alertRepository.findFilteredAlerts(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockProjection), pageable, 1));

        Page<AlertDto> result = alertService.searchAlerts(filter, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDeviceName()).isEqualTo("Core Switch");
    }

    @Test
    void triggerAlert_persistsAndPublishesEvent() {
        when(alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(eq(1L), eq(AlertType.HIGH_LATENCY), any()))
                .thenReturn(List.of());
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        alertService.triggerAlert(sampleDevice, "Latency 250ms", AlertType.HIGH_LATENCY);

        verify(alertRepository).save(any(Alert.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }
}
