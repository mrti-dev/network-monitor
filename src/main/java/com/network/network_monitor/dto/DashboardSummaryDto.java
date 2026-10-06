package com.network.network_monitor.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private long totalDevices;
    private long onlineDevices;
    private long warningDevices;
    private long offlineDevices;
    private long unknownDevices;
    private long maintenanceDevices;
    private long monitoredDevices;

    private long totalOpenAlerts;
    private long triggeredAlerts;
    private long acknowledgedAlerts;
    private long resolvedAlerts;

    private long criticalAlerts;
    private long warningAlerts;
    private long infoAlerts;

    private double averageLatencyMs;
    private double reachabilityPercentage;
    private boolean hasMetricData;

    public boolean getHasMetricData() {
        return hasMetricData;
    }

    public boolean isHasMetricData() {
        return hasMetricData;
    }

    private List<TopDeviceLatencyDto> topLatencyDevices;
    private List<TopDeviceAlertDto> topAlertDevices;
    private List<AlertDto> recentAlerts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopDeviceLatencyDto {
        private Long deviceId;
        private String deviceName;
        private String ipAddress;
        private double averageLatencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopDeviceAlertDto {
        private Long deviceId;
        private String deviceName;
        private String ipAddress;
        private long alertCount;
    }
}
