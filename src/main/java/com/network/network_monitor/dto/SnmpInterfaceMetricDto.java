package com.network.network_monitor.dto;

import java.time.LocalDateTime;
import com.network.network_monitor.enums.SnmpInterfaceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpInterfaceMetricDto {
    private Long id;
    private Long deviceId;
    private Integer interfaceIndex;
    private String interfaceName;
    private SnmpInterfaceStatus operationalStatus;
    private Long inboundOctets;
    private Long outboundOctets;
    private LocalDateTime recordedAt;
}
