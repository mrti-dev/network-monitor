package com.network.network_monitor.scheduler;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.strategy.PingStrategy;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

class PollingSchedulerTest {

    @Test
    void respectsPerDeviceInterval() {
        DeviceRepository devices = mock(DeviceRepository.class);
        MetricLogRepository metrics = mock(MetricLogRepository.class);
        PingStrategy ping = mock(PingStrategy.class);
        HealthEvaluator evaluator = mock(HealthEvaluator.class);
        MonitoringDefaults defaults = new MonitoringDefaults();
        Device device = Device.builder().id(1L).ipAddress("192.168.1.2").build();
        device.setMonitoringConfig(MonitoringConfig.builder().device(device).pingInterval(60).build());
        when(devices.findByIsMonitoredTrue()).thenReturn(List.of(device));
        when(ping.probe(device)).thenReturn(ProbeResult.builder().isReachable(true).latencyMs(1.0).packetLoss(0.0).build());
        PollingScheduler scheduler = new PollingScheduler(devices, metrics, ping, evaluator, defaults);

        scheduler.pollDevices();
        scheduler.pollDevices();

        verify(ping, times(1)).probe(device);
        verify(metrics, times(1)).save(any());
        verify(evaluator, times(1)).evaluate(eq(device), any());
    }
}
