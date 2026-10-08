package com.network.network_monitor.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpMetricDto {
    private Long id;
    private Long deviceId;
    private Long sysUptime;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double inboundBps;
    private Double outboundBps;
    private LocalDateTime recordedAt;
}
