package com.network.network_monitor.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.service.DeviceHealthService;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HealthEvaluator {

    private final DeviceHealthService deviceHealthService;
    
    // In-memory counter for anti-flapping. Capped at 10 to avoid memory leak or unbounded counters.
    private final Map<Long, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<Long, Integer> successCounts = new ConcurrentHashMap<>();

    public void evaluate(Device device, ProbeResult result) {
        if (device.getStatus() == DeviceStatus.MAINTENANCE) {
            return; // Bỏ qua thiết bị đang bảo trì
        }
        
        Long deviceId = device.getId();
        Double threshold = (device.getMonitoringConfig() != null && device.getMonitoringConfig().getLatencyThreshold() != null)
                ? device.getMonitoringConfig().getLatencyThreshold()
                : 150.0;
        
        if (!result.isReachable()) {
            successCounts.remove(deviceId);
            int fails = failureCounts.merge(deviceId, 1, Integer::sum);
            if (fails > 10) { failureCounts.put(deviceId, 10); fails = 10; }
            
            if (fails >= 3 && device.getStatus() != DeviceStatus.OFFLINE) {
                deviceHealthService.processEvaluation(deviceId, DeviceStatus.OFFLINE, 
                        AlertType.OFFLINE, "Thiết bị mất kết nối (OFFLINE) sau 3 lần thử", null);
            }
        } else {
            failureCounts.remove(deviceId);
            
            if (result.getLatencyMs() > threshold) {
                successCounts.remove(deviceId);
                
                if (device.getStatus() != DeviceStatus.WARNING) {
                    deviceHealthService.processEvaluation(deviceId, DeviceStatus.WARNING, 
                            AlertType.HIGH_LATENCY, "Độ trễ cao vượt ngưỡng " + threshold + "ms", 
                            List.of(AlertType.OFFLINE));
                }
            } else {
                int successes = successCounts.merge(deviceId, 1, Integer::sum);
                if (successes > 10) { successCounts.put(deviceId, 10); successes = 10; }
                
                if (successes >= 2 && device.getStatus() != DeviceStatus.ONLINE) {
                    deviceHealthService.processEvaluation(deviceId, DeviceStatus.ONLINE, 
                            null, null, List.of(AlertType.OFFLINE, AlertType.HIGH_LATENCY));
                }
            }
        }
    }

    public void resetCounters(Long deviceId) {
        failureCounts.remove(deviceId);
        successCounts.remove(deviceId);
    }
}
