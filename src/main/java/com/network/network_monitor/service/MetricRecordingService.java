package com.network.network_monitor.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.notification.MetricRecordedEvent;
import com.network.network_monitor.repository.MetricLogRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetricRecordingService {

    private final MetricLogRepository metricLogRepository;
    private final ApplicationEventPublisher events;

    @Autowired
    public MetricRecordingService(MetricLogRepository metricLogRepository,
                                  @Autowired(required = false) ApplicationEventPublisher events) {
        this.metricLogRepository = metricLogRepository;
        this.events = events;
    }

    @Transactional
    public MetricLog recordMetric(Device device, Double latencyMs, Double packetLoss, boolean reachable, LocalDateTime recordedAt) {
        MetricLog metric = MetricLog.builder()
                .device(device)
                .latencyMs(latencyMs)
                .packetLoss(packetLoss)
                .isReachable(reachable)
                .recordedAt(recordedAt)
                .build();

        MetricLog saved = metricLogRepository.save(metric);
        Long metricId = saved != null ? saved.getId() : null;
        Double lat = saved != null ? saved.getLatencyMs() : metric.getLatencyMs();
        Double loss = saved != null ? saved.getPacketLoss() : metric.getPacketLoss();
        Boolean isReach = saved != null ? saved.getIsReachable() : metric.getIsReachable();
        LocalDateTime recAt = saved != null ? saved.getRecordedAt() : metric.getRecordedAt();

        if (events != null) {
            events.publishEvent(new MetricRecordedEvent(
                    metricId,
                    device.getId(),
                    lat,
                    loss,
                    Boolean.TRUE.equals(isReach),
                    recAt
            ));
        }
        return saved != null ? saved : metric;
    }
}
