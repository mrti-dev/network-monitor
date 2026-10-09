package com.network.network_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceStatusHistory;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.DeviceStatusHistoryRepository;
import com.network.network_monitor.repository.MetricLogRepository;

@ExtendWith(MockitoExtension.class)
class DemoSlaDataSeederTest {

    @Mock DeviceRepository deviceRepository;
    @Mock DeviceStatusHistoryRepository statusHistoryRepository;
    @Mock MetricLogRepository metricLogRepository;

    @Test
    void createsOnlyDedicatedUnmonitoredDemoDevices() {
        when(deviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DemoSlaDataSeeder(deviceRepository, statusHistoryRepository, metricLogRepository)
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<Device> devices = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository, times(2)).save(devices.capture());
        assertThat(devices.getAllValues()).allSatisfy(device -> {
            assertThat(device.getName()).startsWith("[DEMO SLA]");
            assertThat(device.getIsMonitored()).isFalse();
            assertThat(device.getIpAddress()).startsWith("198.51.100.");
        });
        ArgumentCaptor<DeviceStatusHistory> histories = ArgumentCaptor.forClass(DeviceStatusHistory.class);
        verify(statusHistoryRepository, times(6)).save(histories.capture());
        assertThat(histories.getAllValues())
                .allSatisfy(history -> assertThat(history.getSource()).isEqualTo(DemoSlaDataSeeder.SIMULATED_DATA_SOURCE));
        verify(metricLogRepository, times(48)).save(any());
    }

    @Test
    void doesNotModifyExistingDevices() {
        when(deviceRepository.findByIpAddressIncludingDeleted(any())).thenReturn(Optional.of(new Device()));

        new DemoSlaDataSeeder(deviceRepository, statusHistoryRepository, metricLogRepository)
                .run(new DefaultApplicationArguments());

        verify(deviceRepository, never()).save(any());
        verify(statusHistoryRepository, never()).save(any());
        verify(metricLogRepository, never()).save(any());
    }
}
