package com.network.network_monitor.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ScanResponseDto {
    private int discovered;
    private int added;
    private int reactivated;
    private int skipped;
    private int failed;
    /** true nếu scan bị dừng sớm do timeout tổng — kết quả chỉ là một phần. */
    @Builder.Default
    private boolean partial = false;
    private List<DiscoveredDevice> details;

    @Data
    @Builder
    public static class DiscoveredDevice {
        private String ipAddress;
        private String macAddress;
        private double latencyMs;
        private boolean isNew;
        private DiscoveryOutcome outcome;
    }
}

