package com.network.network_monitor.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.service.DeviceHealthService;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

@ExtendWith(MockitoExtension.class)
class HealthEvaluatorTest {

    @Mock
    private DeviceHealthService deviceHealthService;

    @InjectMocks
    private HealthEvaluator healthEvaluator;

    private Device device;
    private MonitoringConfig config;

    @BeforeEach
    void setUp() {
        config = new MonitoringConfig();
        config.setLatencyThreshold(150.0);

        device = new Device();
        device.setId(1L);
        device.setStatus(DeviceStatus.UNKNOWN);
        device.setMonitoringConfig(config);
        
        healthEvaluator.resetCounters(1L);
    }

    @Test
    void shouldGoOfflineAfter3ConsecutiveFailures() {
        ProbeResult failedProbe = ProbeResult.builder().isReachable(false).latencyMs(0.0).packetLoss(100.0).build();

        // 1st fail
        healthEvaluator.evaluate(device, failedProbe);
        verify(deviceHealthService, never()).processEvaluation(any(), any(), any(), anyString(), any());

        // 2nd fail
        healthEvaluator.evaluate(device, failedProbe);
        verify(deviceHealthService, never()).processEvaluation(any(), any(), any(), anyString(), any());

        // 3rd fail -> OFFLINE
        healthEvaluator.evaluate(device, failedProbe);
        verify(deviceHealthService, times(1)).processEvaluation(
                eq(1L), eq(DeviceStatus.OFFLINE), eq(AlertType.OFFLINE), anyString(), eq(null));
    }

    @Test
    void shouldGoOnlineAfter2ConsecutiveSuccesses() {
        device.setStatus(DeviceStatus.OFFLINE); // currently offline
        ProbeResult successProbe = ProbeResult.builder().isReachable(true).latencyMs(50.0).packetLoss(0.0).build();

        // 1st success
        healthEvaluator.evaluate(device, successProbe);
        verify(deviceHealthService, never()).processEvaluation(any(), any(), any(), any(), any());

        // 2nd success -> ONLINE
        healthEvaluator.evaluate(device, successProbe);
        verify(deviceHealthService, times(1)).processEvaluation(
                eq(1L), eq(DeviceStatus.ONLINE), eq(null), eq(null), eq(List.of(AlertType.OFFLINE, AlertType.HIGH_LATENCY)));
    }

    @Test
    void shouldGoWarningIfLatencyExceedsThreshold() {
        device.setStatus(DeviceStatus.ONLINE);
        // success but high latency
        ProbeResult warningProbe = ProbeResult.builder().isReachable(true).latencyMs(200.0).packetLoss(0.0).build(); 

        // 1st warning probe should immediately trigger WARNING state 
        // (no consecutive counts needed based on logic)
        healthEvaluator.evaluate(device, warningProbe);

        verify(deviceHealthService, times(1)).processEvaluation(
                eq(1L), eq(DeviceStatus.WARNING), eq(AlertType.HIGH_LATENCY), anyString(), eq(List.of(AlertType.OFFLINE)));
    }

    @Test
    void shouldNotChangeStateIfMaintenance() {
        device.setStatus(DeviceStatus.MAINTENANCE);
        ProbeResult failedProbe = ProbeResult.builder().isReachable(false).latencyMs(0.0).packetLoss(100.0).build();
        
        // 5 fails
        for (int i = 0; i < 5; i++) {
            healthEvaluator.evaluate(device, failedProbe);
        }

        verify(deviceHealthService, never()).processEvaluation(any(), any(), any(), any(), any());
    }

    @Test
    void shouldResetFailureCountOnSuccess() {
        ProbeResult failedProbe = ProbeResult.builder().isReachable(false).latencyMs(0.0).packetLoss(100.0).build();
        ProbeResult successProbe = ProbeResult.builder().isReachable(true).latencyMs(50.0).packetLoss(0.0).build();

        healthEvaluator.evaluate(device, failedProbe); // 1 fail
        healthEvaluator.evaluate(device, failedProbe); // 2 fails
        
        healthEvaluator.evaluate(device, successProbe); // success resets failure

        healthEvaluator.evaluate(device, failedProbe); // 1 fail again
        
        verify(deviceHealthService, never()).processEvaluation(
                eq(1L), eq(DeviceStatus.OFFLINE), any(), anyString(), any());
    }
}
