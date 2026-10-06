package com.network.network_monitor.strategy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.TcpPortMonitor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TcpProbingStrategy {

    private final MonitoringDefaults defaults;
    private final Clock clock;

    @Autowired
    public TcpProbingStrategy(MonitoringDefaults defaults, @Autowired(required = false) Clock clock) {
        this.defaults = defaults;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public TcpProbeResult probe(Device device, TcpPortMonitor monitor) {
        int timeout = monitor.getTimeoutMs() != null && monitor.getTimeoutMs() > 0
                ? monitor.getTimeoutMs()
                : defaults.getTimeoutMs();
        return probe(device.getIpAddress(), monitor.getPort(), timeout);
    }

    public TcpProbeResult probe(String ipAddress, int port, int timeoutMs) {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        try (Socket socket = new Socket()) {
            long startNanos = System.nanoTime();
            socket.connect(new InetSocketAddress(ipAddress, port), timeoutMs);
            double latency = (System.nanoTime() - startNanos) / 1_000_000.0;
            return TcpProbeResult.success(latency, checkedAt);
        } catch (ConnectException e) {
            log.debug("TCP connect to {}:{} refused: {}", ipAddress, port, e.getMessage());
            return TcpProbeResult.failure("Connection refused", checkedAt);
        } catch (SocketTimeoutException e) {
            log.debug("TCP connect to {}:{} timed out after {}ms", ipAddress, port, timeoutMs);
            return TcpProbeResult.failure("Connection timed out", checkedAt);
        } catch (UnknownHostException e) {
            log.debug("TCP connect to {}:{} unknown host", ipAddress, port);
            return TcpProbeResult.failure("Unknown host", checkedAt);
        } catch (IOException e) {
            log.debug("TCP connect to {}:{} I/O error: {}", ipAddress, port, e.getMessage());
            return TcpProbeResult.failure("I/O error: " + e.getMessage(), checkedAt);
        } catch (Exception e) {
            log.debug("TCP connect to {}:{} unexpected error: {}", ipAddress, port, e.getMessage());
            return TcpProbeResult.failure("Error: " + e.getMessage(), checkedAt);
        }
    }
}
