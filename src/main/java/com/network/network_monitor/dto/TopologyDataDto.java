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
public class TopologyDataDto {
    private List<TopologyNodeDto> nodes;
    private List<NetworkLinkDto> links;
    private int totalNodes;
    private int onlineNodes;
    private int offlineNodes;
    private int totalLinks;
    private int upLinks;
    private int downLinks;
}
