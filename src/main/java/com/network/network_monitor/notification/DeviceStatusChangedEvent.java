package com.network.network_monitor.notification;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.network.network_monitor.enums.DeviceStatus;

public record DeviceStatusChangedEvent(
        Long deviceId,
        String deviceName,
        String ipAddress,
        DeviceStatus previousStatus,
        DeviceStatus currentStatus,
        String reason,
        LocalDateTime timestamp
) {
    @JsonIgnore
    public String name() {
        return deviceName;
    }
}
