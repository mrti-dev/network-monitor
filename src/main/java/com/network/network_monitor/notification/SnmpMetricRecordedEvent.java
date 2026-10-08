package com.network.network_monitor.notification;

import java.time.LocalDateTime;

public record SnmpMetricRecordedEvent(
        Long metricId,
        Long deviceId,
        Long sysUptime,
        Double cpuUsage,
        Double memoryUsage,
        Double inboundBps,
        Double outboundBps,
        LocalDateTime recordedAt
) {}
