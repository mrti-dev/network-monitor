package com.network.network_monitor.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.service.MetricRecordingService;
import com.network.network_monitor.strategy.PingStrategy;
import com.network.network_monitor.strategy.ProbingStrategy.ProbeResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PollingScheduler {

    private final DeviceRepository deviceRepository;
    private final MetricRecordingService metricRecordingService;
    private final PingStrategy pingStrategy;
    private final HealthEvaluator healthEvaluator;
    private final MonitoringDefaults defaults;
    private final ConcurrentHashMap<Long, Long> lastPollNanos = new ConcurrentHashMap<>();

    @Autowired
    public PollingScheduler(DeviceRepository deviceRepository,
                            MetricRecordingService metricRecordingService,
                            PingStrategy pingStrategy,
                            HealthEvaluator healthEvaluator,
                            MonitoringDefaults defaults) {
        this.deviceRepository = deviceRepository;
        this.metricRecordingService = metricRecordingService;
        this.pingStrategy = pingStrategy;
        this.healthEvaluator = healthEvaluator;
        this.defaults = defaults;
    }

    public PollingScheduler(DeviceRepository deviceRepository,
                            MetricLogRepository metricLogRepository,
                            PingStrategy pingStrategy,
                            HealthEvaluator healthEvaluator,
                            MonitoringDefaults defaults) {
        this(deviceRepository, new MetricRecordingService(metricLogRepository, (e) -> {}), pingStrategy, healthEvaluator, defaults);
    }

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
                        : (device.getGroup() != null && device.getGroup().getPingInterval() != null
                                ? device.getGroup().getPingInterval()
                                : defaults.getPingInterval());
                long intervalNanos = TimeUnit.SECONDS.toNanos(interval);
                Long lastPoll = lastPollNanos.get(device.getId());
                if (lastPoll != null && now - lastPoll < intervalNanos) continue;
                lastPollNanos.put(device.getId(), now);
                executor.submit(() -> {
                    try {
                        ProbeResult result = pingStrategy.probe(device);
                        metricRecordingService.recordMetric(device, result.getLatencyMs(), result.getPacketLoss(), result.isReachable(), LocalDateTime.now());
                        healthEvaluator.evaluate(device, result);
                    } catch (Exception e) {
                        log.error("Lỗi khi poll device {}", device.getIpAddress(), e);
                    }
                });
            }
        }
    }
}
