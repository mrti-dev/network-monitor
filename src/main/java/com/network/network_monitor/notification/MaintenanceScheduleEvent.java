package com.network.network_monitor.notification;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.MaintenanceAction;
import com.network.network_monitor.enums.MaintenanceStatus;

public record MaintenanceScheduleEvent(
        Long scheduleId,
        Long deviceId,
        String deviceName,
        String title,
        MaintenanceAction action,
        MaintenanceStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime timestamp
) {}
