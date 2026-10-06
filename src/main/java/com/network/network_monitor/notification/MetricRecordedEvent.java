package com.network.network_monitor.notification;

import java.time.LocalDateTime;

public record MetricRecordedEvent(
        Long metricId,
        Long deviceId,
        Double latencyMs,
        Double packetLoss,
        boolean reachable,
        LocalDateTime recordedAt
) {}
