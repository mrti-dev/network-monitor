package com.network.network_monitor.strategy;

import java.util.Collections;
import java.util.List;

import com.network.network_monitor.dto.LldpNeighborDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LldpProbeResult {
    private final boolean reachable;
    private final boolean lldpSupported;
    @Builder.Default
    private final List<LldpNeighborDto> neighbors = Collections.emptyList();
    private final String failureReason;
}
