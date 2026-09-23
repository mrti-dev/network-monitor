package com.network.network_monitor.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.LatencyThresholdProjection;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceRepository;

class DeviceMappingTest {
    private final DeviceRepository repository = mock(DeviceRepository.class);
    private final MonitoringDefaults defaults = new MonitoringDefaults();
    private final DeviceServiceImpl service = new DeviceServiceImpl(repository, null, defaults, null, null);

    @Test
    void editMapperNeverTouchesLazyConfig() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(device));
        assertThat(service.getDeviceFormById(1L).getId()).isEqualTo(1);
        verify(device, never()).getMonitoringConfig();
    }

    @Test
    void thresholdUsesProjectionAndConfiguredFallback() {
        defaults.setLatencyThreshold(321);
        var projection = mock(LatencyThresholdProjection.class);
        when(projection.getLatencyThreshold()).thenReturn(null);
        when(repository.findLatencyThreshold(1L)).thenReturn(Optional.of(projection));
        assertThat(service.getLatencyThreshold(1L)).isEqualTo(321);
        when(projection.getLatencyThreshold()).thenReturn(42.0);
        assertThat(service.getLatencyThreshold(1L)).isEqualTo(42);
        assertThatThrownBy(() -> service.getLatencyThreshold(2L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).findById(any());
    }
}
