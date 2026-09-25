package com.network.network_monitor.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.LatencyThresholdProjection;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceRepository;

class DeviceMappingTest {
    private final DeviceRepository repository = mock(DeviceRepository.class);
    private final MonitoringDefaults defaults = new MonitoringDefaults();
    private final DeviceServiceImpl service = new DeviceServiceImpl(repository, null, defaults, null, null);

    @Test
    void editMapperIncludesMonitoringConfig() {
        Device device = mock(Device.class);
        MonitoringConfig config = MonitoringConfig.builder()
                .pingInterval(30).timeoutMs(3000).latencyThreshold(200.0).build();
        when(device.getId()).thenReturn(1L);
        when(device.getMonitoringConfig()).thenReturn(config);
        when(repository.findById(1L)).thenReturn(Optional.of(device));
        var form = service.getDeviceFormById(1L);
        assertThat(form.getId()).isEqualTo(1);
        assertThat(form.getPingInterval()).isEqualTo(30);
        assertThat(form.getTimeoutMs()).isEqualTo(3000);
        assertThat(form.getLatencyThreshold()).isEqualTo(200);
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
