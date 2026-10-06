package com.network.network_monitor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.AlertService;

@ExtendWith(MockitoExtension.class)
class DeviceHealthServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AlertService alertService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeviceHealthServiceImpl deviceHealthService;

    private Device device;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setName("Router");
        device.setIpAddress("192.168.1.1");
        device.setStatus(DeviceStatus.UNKNOWN);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    }

    @Test
    void test1_shouldPublishOneEventWhenUnknownToOnline() {
        device.setStatus(DeviceStatus.UNKNOWN);

        deviceHealthService.processEvaluation(1L, DeviceStatus.ONLINE, null, null, null);

        verify(deviceRepository, times(1)).save(device);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);

        ArgumentCaptor<DeviceStatusChangedEvent> captor = ArgumentCaptor.forClass(DeviceStatusChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        DeviceStatusChangedEvent event = captor.getValue();
        assertThat(event.deviceId()).isEqualTo(1L);
        assertThat(event.deviceName()).isEqualTo("Router");
        assertThat(event.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(event.previousStatus()).isEqualTo(DeviceStatus.UNKNOWN);
        assertThat(event.currentStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(event.reason()).isEqualTo("Thiết bị phản hồi ổn định");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void test2_shouldPublishOneEventWhenOnlineToOffline() {
        device.setStatus(DeviceStatus.ONLINE);

        deviceHealthService.processEvaluation(1L, DeviceStatus.OFFLINE, AlertType.OFFLINE,
                "Thiết bị mất kết nối (OFFLINE) sau 3 lần thử", null);

        verify(deviceRepository, times(1)).save(device);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);

        ArgumentCaptor<DeviceStatusChangedEvent> captor = ArgumentCaptor.forClass(DeviceStatusChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        DeviceStatusChangedEvent event = captor.getValue();
        assertThat(event.deviceId()).isEqualTo(1L);
        assertThat(event.deviceName()).isEqualTo("Router");
        assertThat(event.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(event.previousStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(event.currentStatus()).isEqualTo(DeviceStatus.OFFLINE);
        assertThat(event.reason()).isEqualTo("Thiết bị mất kết nối (OFFLINE) sau 3 lần thử");
        assertThat(event.timestamp()).isNotNull();
        verify(alertService, times(1)).triggerAlert(device, "Thiết bị mất kết nối (OFFLINE) sau 3 lần thử", AlertType.OFFLINE);
    }

    @Test
    void test3_shouldNotPublishEventWhenStatusUnchangedOnlineToOnline() {
        device.setStatus(DeviceStatus.ONLINE);

        deviceHealthService.processEvaluation(1L, DeviceStatus.ONLINE, null, null, null);

        verify(deviceRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void test4_shouldNotUpdateOrPublishWhenMaintenance() {
        device.setStatus(DeviceStatus.MAINTENANCE);

        deviceHealthService.processEvaluation(1L, DeviceStatus.OFFLINE, AlertType.OFFLINE,
                "Device is offline", null);

        verify(deviceRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(alertService, never()).triggerAlert(any(), any(), any());
    }

    @Test
    void shouldResolveAlertsWhenGoingOnline() {
        device.setStatus(DeviceStatus.OFFLINE);

        deviceHealthService.processEvaluation(1L, DeviceStatus.ONLINE, null,
                null, List.of(AlertType.OFFLINE, AlertType.HIGH_LATENCY));

        verify(deviceRepository, times(1)).save(device);
        verify(alertService, times(1)).resolveAlerts(device, AlertType.OFFLINE);
        verify(alertService, times(1)).resolveAlerts(device, AlertType.HIGH_LATENCY);
        verify(alertService, never()).triggerAlert(any(), any(), any());
        verify(eventPublisher, times(1)).publishEvent(any(DeviceStatusChangedEvent.class));
    }

    @Test
    void shouldNotUpdateStatusIfUnchanged() {
        device.setStatus(DeviceStatus.WARNING);

        deviceHealthService.processEvaluation(1L, DeviceStatus.WARNING, AlertType.HIGH_LATENCY,
                "High latency", null);

        verify(deviceRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(alertService, times(1)).triggerAlert(device, "High latency", AlertType.HIGH_LATENCY);
    }
}
