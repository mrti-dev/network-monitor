package com.network.network_monitor.dto;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.TcpPortStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TcpPortDto {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private Integer port;
    private String serviceName;
    private Boolean enabled;
    private Integer timeoutMs;
    private Integer failureThreshold;
    private Integer successThreshold;
    private TcpPortStatus status;
    private Integer consecutiveFailures;
    private Integer consecutiveSuccesses;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime lastStatusChangedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
