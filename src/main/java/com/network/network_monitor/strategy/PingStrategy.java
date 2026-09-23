package com.network.network_monitor.strategy;

import java.net.InetAddress;

import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;

import com.network.network_monitor.config.MonitoringDefaults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PingStrategy implements ProbingStrategy {
    private final MonitoringDefaults defaults;

    @Override
    public ProbeResult probe(Device device) {
        MonitoringConfig config = device.getMonitoringConfig();
        int timeoutMs = config != null && config.getTimeoutMs() != null ? config.getTimeoutMs() : defaults.getTimeoutMs();

        try {
            long startTime = System.currentTimeMillis();
            InetAddress inet = InetAddress.getByName(device.getIpAddress());
            if (inet.isReachable(timeoutMs)) {
                long latency = System.currentTimeMillis() - startTime;
                return ProbeResult.builder()
                        .isReachable(true)
                        .latencyMs(latency)
                        .packetLoss(0.0)
                        .build();
            } else {
                return ProbeResult.builder()
                        .isReachable(false)
                        .latencyMs(0)
                        .packetLoss(100.0)
                        .build();
            }
        } catch (Exception e) {
            log.warn("PingStrategy: Không thể kết nối tới IP {}", device.getIpAddress(), e);
            return ProbeResult.builder()
                    .isReachable(false)
                    .latencyMs(0)
                    .packetLoss(100.0)
                    .build();
        }
    }
}
