package com.network.network_monitor.dto;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private Boolean isDeviceDeleted;
    private AlertType alertType;
    private String message;
    private AlertSeverity severity;
    private AlertStatus status;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;
}
