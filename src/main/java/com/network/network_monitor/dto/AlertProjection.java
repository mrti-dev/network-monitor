package com.network.network_monitor.dto;

import java.time.LocalDateTime;

public interface AlertProjection {
    Long getId();
    Long getDeviceId();
    String getDeviceName();
    Boolean getIsDeviceDeleted();
    String getAlertType();
    String getMessage();
    String getSeverity();
    String getStatus();
    String getAcknowledgedBy();
    LocalDateTime getAcknowledgedAt();
    LocalDateTime getTriggeredAt();
    LocalDateTime getResolvedAt();
}
