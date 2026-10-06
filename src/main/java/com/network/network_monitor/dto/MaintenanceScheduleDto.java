package com.network.network_monitor.dto;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.MaintenanceStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceScheduleDto {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private String title;
    private String reason;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private MaintenanceStatus status;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime completedAt;
}
