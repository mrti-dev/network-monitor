package com.network.network_monitor.strategy;

import com.network.network_monitor.entity.Device;
import lombok.Builder;
import lombok.Data;

public interface ProbingStrategy {

    ProbeResult probe(Device device);

    @Data
    @Builder
    class ProbeResult {
        private boolean isReachable;
        private double latencyMs;
        private double packetLoss;
    }
}
