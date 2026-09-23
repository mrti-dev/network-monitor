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
public class MetricLogDto {
    private LocalDateTime recordedAt;
    private Double latencyMs;
    private Double packetLoss;
    private Boolean isReachable;
}
