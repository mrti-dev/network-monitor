package com.network.network_monitor.dto;

import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkLinkDto {
    private Long id;
    private Long sourceDeviceId;
    private String sourceDeviceName;
    private String sourceDeviceIp;
    private Long targetDeviceId;
    private String targetDeviceName;
    private String targetDeviceIp;
    private LinkType linkType;
    private String sourcePort;
    private String targetPort;
    private Integer bandwidthMbps;
    private LinkStatus status;
    private com.network.network_monitor.enums.LinkDiscoverySource discoverySource;
    private java.time.LocalDateTime lastDiscoveredAt;
    private String description;
}
