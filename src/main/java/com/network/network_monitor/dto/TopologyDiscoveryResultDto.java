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
public class TopologyDiscoveryResultDto {
    private int createdCount;
    private int updatedCount;
    private int scannedDevices;
    private int failedDevices;
    private int unsupportedDevices;
}
