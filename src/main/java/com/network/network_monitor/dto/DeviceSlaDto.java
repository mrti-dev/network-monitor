package com.network.network_monitor.dto;

import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.SlaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSlaDto {
    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private DeviceType deviceType;
    private String groupName;

    private Long totalChecks;
    private Long reachableChecks;
    private Long unreachableChecks;

    private Double uptimePercentage;
    private Double targetUptimePercentage;

    private Double avgLatencyMs;
    private Double maxLatencyMs;
    private Double latencyCompliancePercentage;

    private Double avgPacketLoss;
    private Double packetLossCompliancePercentage;

    private Integer totalOutages;
    private Double downtimeMinutes;
    private Double mttrMinutes;
    private Double mttfMinutes;

    private Double tcpAvailabilityPercentage;
    private Double snmpAvailabilityPercentage;

    private SlaStatus slaStatus;
}
