package com.network.network_monitor.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ScanResponseDto {
    private int discovered;
    private int added;
    private int skipped;
    private List<DiscoveredDevice> details;

    @Data
    @Builder
    public static class DiscoveredDevice {
        private String ipAddress;
        private String macAddress;
        private double latencyMs;
        private boolean isNew;
    }
}
