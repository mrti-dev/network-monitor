package com.network.network_monitor.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.config.MonitoringDefaults;
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
    private final MonitoringDefaults defaults;
    private final ConcurrentHashMap<Long, Long> lastPollNanos = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${poll.scheduler.tick-ms:1000}")
    public void pollDevices() {
        List<Device> monitoredDevices = deviceRepository.findByIsMonitoredTrue();
        Set<Long> monitoredIds = monitoredDevices.stream().map(Device::getId).collect(java.util.stream.Collectors.toSet());
        lastPollNanos.keySet().retainAll(monitoredIds);
        long now = System.nanoTime();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Device device : monitoredDevices) {
                int interval = device.getMonitoringConfig() != null && device.getMonitoringConfig().getPingInterval() != null
                        ? device.getMonitoringConfig().getPingInterval()
                        : defaults.getPingInterval();
                long intervalNanos = TimeUnit.SECONDS.toNanos(interval);
                Long lastPoll = lastPollNanos.get(device.getId());
                if (lastPoll != null && now - lastPoll < intervalNanos) continue;
                lastPollNanos.put(device.getId(), now);
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
