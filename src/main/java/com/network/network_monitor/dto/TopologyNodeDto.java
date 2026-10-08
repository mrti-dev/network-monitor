package com.network.network_monitor.dto;

import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyNodeDto {
    private Long id;
    private String name;
    private String ipAddress;
    private String macAddress;
    private DeviceType deviceType;
    private DeviceStatus status;
    private String location;
    private String groupName;
    private Boolean isMonitored;
    private Double latencyMs;
    private Double packetLoss;
    private Double cpuUsage;
    private Double memoryUsage;
}
