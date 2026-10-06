package com.network.network_monitor.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.repository.TcpPortMonitorRepository;
import com.network.network_monitor.service.TcpPortMonitorService;
import com.network.network_monitor.strategy.TcpProbeResult;
import com.network.network_monitor.strategy.TcpProbingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpPortPollingScheduler {

    private final TcpPortMonitorRepository portRepository;
    private final TcpProbingStrategy probingStrategy;
    private final TcpPortMonitorService portService;

    @Value("${tcp.scheduler.max-concurrency:50}")
    private int maxConcurrency = 50;

    @Scheduled(fixedDelayString = "${tcp.scheduler.tick-ms:3000}")
    public void pollMonitoredPorts() {
        List<TcpPortMonitor> activePorts = portRepository.findActiveMonitoredPorts();
        if (activePorts.isEmpty()) {
            return;
        }

        int concurrency = Math.max(1, maxConcurrency);
        try (var executor = Executors.newFixedThreadPool(concurrency)) {
            for (int offset = 0; offset < activePorts.size(); offset += concurrency) {
                List<Callable<Void>> batch = new ArrayList<>();
                for (TcpPortMonitor monitor : activePorts.subList(offset, Math.min(offset + concurrency, activePorts.size()))) {
                    batch.add(() -> {
                        probe(monitor);
                        return null;
                    });
                }
                executor.invokeAll(batch);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void probe(TcpPortMonitor monitor) {
        try {
            TcpProbeResult result = probingStrategy.probe(monitor.getDevice(), monitor);
            portService.evaluateProbeResult(monitor.getId(), result);
        } catch (Exception e) {
            log.debug("Lỗi khi thăm dò TCP port {} trên thiết bị {}: {}",
                    monitor.getPort(),
                    monitor.getDevice() != null ? monitor.getDevice().getIpAddress() : "N/A",
                    e.getMessage());
        }
    }
}
