package com.network.network_monitor.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.network.network_monitor.enums.SlaPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaSummaryReportDto {
    private SlaPeriod period;
    private LocalDateTime fromDateTime;
    private LocalDateTime toDateTime;

    private Double overallUptimePercentage;
    private Double overallLatencyCompliancePercentage;
    private Double avgNetworkLatencyMs;

    private int totalDevicesCount;
    private int metCount;
    private int atRiskCount;
    private int breachedCount;

    private int totalOutages;
    private Double avgMttrMinutes;

    private List<DeviceSlaDto> deviceSlaList;
}
