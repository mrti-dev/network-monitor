package com.network.network_monitor.service;

import java.util.List;

import com.network.network_monitor.dto.NetworkLinkDto;
import com.network.network_monitor.dto.NetworkLinkFormDto;
import com.network.network_monitor.dto.TopologyDataDto;
import com.network.network_monitor.dto.TopologyDiscoveryResultDto;

public interface TopologyService {
    TopologyDataDto getTopologyData();
    List<NetworkLinkDto> getAllLinks();
    NetworkLinkDto createLink(NetworkLinkFormDto form);
    NetworkLinkDto updateLink(Long id, NetworkLinkFormDto form);
    void deleteLink(Long id);
    int autoDiscoverLinks();
    TopologyDiscoveryResultDto autoDiscoverLinksDetailed();
}
