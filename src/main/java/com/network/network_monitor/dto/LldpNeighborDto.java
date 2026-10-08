package com.network.network_monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LldpNeighborDto {
    private Integer localPortNum;
    private String localPort;
    private String remoteChassisId;
    private String remoteChassisMac;
    private String remotePortId;
    private String remotePortDesc;
    private String remoteSysName;
    private String remoteManagementIp;
}
