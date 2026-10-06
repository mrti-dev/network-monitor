package com.network.network_monitor.notification;

import java.time.LocalDateTime;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;

public record AlertEvent(
        Long alertId,
        Long deviceId,
        String deviceName,
        AlertType alertType,
        String message,
        AlertSeverity severity,
        AlertStatus status,
        String action,
        LocalDateTime timestamp
) {}
