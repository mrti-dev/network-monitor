package com.network.network_monitor.dto;

import com.network.network_monitor.enums.DeviceType;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
        private DeviceType deviceType;
        private double latencyMs;
        private boolean isNew;
        private DiscoveryOutcome outcome;
    }
}

