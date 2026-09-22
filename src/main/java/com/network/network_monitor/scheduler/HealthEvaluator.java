package com.network.network_monitor.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HealthEvaluator {

    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    
    // In-memory counter for anti-flapping
    private final Map<Long, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<Long, Integer> successCounts = new ConcurrentHashMap<>();

    public void evaluate(Device device, ProbeResult result) {
        if (device.getStatus() == DeviceStatus.MAINTENANCE) {
            return; // Bỏ qua thiết bị đang bảo trì
        }
        
        Long deviceId = device.getId();
        
        if (!result.isReachable()) {
            successCounts.put(deviceId, 0);
            int fails = failureCounts.getOrDefault(deviceId, 0) + 1;
            failureCounts.put(deviceId, fails);
            
            if (fails >= 3 && device.getStatus() != DeviceStatus.OFFLINE) {
                device.setStatus(DeviceStatus.OFFLINE);
                deviceRepository.save(device);
                alertService.triggerAlert(device, "Thiết bị mất kết nối (OFFLINE) sau 3 lần thử", com.network.network_monitor.enums.AlertType.OFFLINE);
            } else if (fails < 3 && device.getStatus() != DeviceStatus.WARNING && result.getLatencyMs() > 150.0) {
                device.setStatus(DeviceStatus.WARNING);
                deviceRepository.save(device);
            }
        } else {
            failureCounts.put(deviceId, 0);
            int successes = successCounts.getOrDefault(deviceId, 0) + 1;
            successCounts.put(deviceId, successes);
            
            if (successes >= 2 && device.getStatus() != DeviceStatus.ONLINE) {
                device.setStatus(DeviceStatus.ONLINE);
                deviceRepository.save(device);
                alertService.resolveAlerts(device);
            }
        }
    }
}
