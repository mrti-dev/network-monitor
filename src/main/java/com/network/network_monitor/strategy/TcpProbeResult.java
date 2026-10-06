package com.network.network_monitor.strategy;

import java.time.LocalDateTime;

public record TcpProbeResult(
        boolean reachable,
        double latencyMs,
        String reason,
        LocalDateTime checkedAt
) {
    public static TcpProbeResult success(double latencyMs, LocalDateTime checkedAt) {
        return new TcpProbeResult(true, latencyMs, "OK", checkedAt);
    }

    public static TcpProbeResult failure(String reason, LocalDateTime checkedAt) {
        return new TcpProbeResult(false, 0.0, reason, checkedAt);
    }
}
