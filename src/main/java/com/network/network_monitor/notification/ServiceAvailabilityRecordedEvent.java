package com.network.network_monitor.notification;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.ServiceType;

public record ServiceAvailabilityRecordedEvent(
        Long deviceId,
        ServiceType serviceType,
        Long monitorId,
        boolean available,
        LocalDateTime recordedAt
) {
}
