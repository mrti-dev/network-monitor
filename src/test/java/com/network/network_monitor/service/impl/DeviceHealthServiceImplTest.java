package com.network.network_monitor.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.AlertService;

@ExtendWith(MockitoExtension.class)
class DeviceHealthServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private DeviceHealthServiceImpl deviceHealthService;

    private Device device;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setStatus(DeviceStatus.UNKNOWN);
        
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
    }

    @Test
    void shouldUpdateStatusAndTriggerAlert() {
        deviceHealthService.processEvaluation(1L, DeviceStatus.OFFLINE, AlertType.OFFLINE, 
                "Device is offline", null);
                
        verify(deviceRepository, times(1)).save(device);
        verify(alertService, times(1)).triggerAlert(device, "Device is offline", AlertType.OFFLINE);
        verify(alertService, never()).resolveAlerts(any(), any());
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
    }

    @Test
    void shouldNotUpdateStatusIfUnchanged() {
        device.setStatus(DeviceStatus.WARNING);
        
        // Target status is also WARNING
        deviceHealthService.processEvaluation(1L, DeviceStatus.WARNING, AlertType.HIGH_LATENCY, 
                "High latency", null);
                
        verify(deviceRepository, never()).save(any());
        // Should still trigger alert if applicable (triggerAlert will handle dedup internally)
        verify(alertService, times(1)).triggerAlert(device, "High latency", AlertType.HIGH_LATENCY);
    }
}
