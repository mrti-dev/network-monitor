package com.network.network_monitor.scheduler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.config.SnmpProperties;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.service.SnmpService;
import com.network.network_monitor.strategy.SnmpProbeResult;
import com.network.network_monitor.strategy.SnmpProbingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnmpPollingScheduler {

    private final SnmpConfigRepository configRepository;
    private final SnmpProbingStrategy probingStrategy;
    private final SnmpService snmpService;
    private final SnmpProperties snmpProperties;

    private final ConcurrentHashMap<Long, Long> lastPollNanos = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${app.snmp.tick-ms:3000}")
    public void pollActiveSnmpDevices() {
        List<SnmpConfig> activeConfigs = configRepository.findActiveConfigs();
        if (activeConfigs.isEmpty()) {
            return;
        }

        // Dọn dẹp cache các device không còn active
        Set<Long> activeDeviceIds = activeConfigs.stream()
                .map(c -> c.getDevice().getId())
                .collect(Collectors.toSet());
        lastPollNanos.keySet().retainAll(activeDeviceIds);

        long now = System.nanoTime();
        long intervalNanos = TimeUnit.SECONDS.toNanos(Math.max(5, snmpProperties.getPollingInterval()));
        int maxConcurrency = Math.max(1, snmpProperties.getMaxConcurrency());
        Semaphore semaphore = new Semaphore(maxConcurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (SnmpConfig config : activeConfigs) {
                Device device = config.getDevice();
                if (device == null ||
                        device.getStatus() == DeviceStatus.MAINTENANCE ||
                        Boolean.FALSE.equals(device.getIsMonitored()) ||
                        Boolean.TRUE.equals(device.getIsDeleted()) ||
                        Boolean.FALSE.equals(config.getEnabled())) {
                    continue;
                }

                Long lastPoll = lastPollNanos.get(device.getId());
                if (lastPoll != null && (now - lastPoll) < intervalNanos) {
                    continue;
                }

                lastPollNanos.put(device.getId(), now);

                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            SnmpProbeResult result = probingStrategy.probe(device.getIpAddress(), config);
                            snmpService.processProbeResult(device.getId(), result);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.debug("Lỗi khi thăm dò SNMP thiết bị {}: {}", device.getIpAddress(), e.getMessage());
                    }
                });
            }
        }
    }
}
