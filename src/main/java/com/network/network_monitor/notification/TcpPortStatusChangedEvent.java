package com.network.network_monitor.notification;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.TcpPortStatus;

public record TcpPortStatusChangedEvent(
        Long monitorId,
        Long deviceId,
        String deviceName,
        String ipAddress,
        int port,
        String serviceName,
        TcpPortStatus previousStatus,
        TcpPortStatus currentStatus,
        double latencyMs,
        String reason,
        LocalDateTime timestamp
) {}
