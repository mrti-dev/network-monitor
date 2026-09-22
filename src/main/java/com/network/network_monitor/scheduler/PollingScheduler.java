package com.network.network_monitor.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.strategy.PingStrategy;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PollingScheduler {

    private final DeviceRepository deviceRepository;
    private final MetricLogRepository metricLogRepository;
    private final PingStrategy pingStrategy;
    private final HealthEvaluator healthEvaluator;

    @Scheduled(fixedDelayString = "${poll.global.interval:15000}")
    public void pollDevices() {
        List<Device> monitoredDevices = deviceRepository.findByIsMonitoredTrue();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Device device : monitoredDevices) {
                executor.submit(() -> {
                    try {
                        ProbeResult result = pingStrategy.probe(device);
                        
                        MetricLog metric = MetricLog.builder()
                                .device(device)
                                .latencyMs(result.getLatencyMs())
                                .packetLoss(result.getPacketLoss())
                                .isReachable(result.isReachable())
                                .recordedAt(LocalDateTime.now())
                                .build();
                                
                        metricLogRepository.save(metric);
                        
                        healthEvaluator.evaluate(device, result);
                    } catch (Exception e) {
                        log.error("Lỗi khi poll device {}", device.getIpAddress(), e);
                    }
                });
            }
        }
    }
}
