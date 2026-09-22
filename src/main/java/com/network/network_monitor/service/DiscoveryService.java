package com.network.network_monitor.service;

import com.network.network_monitor.dto.ScanRequestDto;
import com.network.network_monitor.dto.ScanResponseDto;

public interface DiscoveryService {
    ScanResponseDto scanNetwork(ScanRequestDto request);
}
