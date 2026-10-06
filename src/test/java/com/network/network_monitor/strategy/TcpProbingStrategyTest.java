package com.network.network_monitor.strategy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.network.network_monitor.config.MonitoringDefaults;

class TcpProbingStrategyTest {

    private TcpProbingStrategy probingStrategy;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        MonitoringDefaults defaults = new MonitoringDefaults();
        defaults.setTimeoutMs(1000);
        fixedClock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneId.of("UTC"));
        probingStrategy = new TcpProbingStrategy(defaults, fixedClock);
    }

    @Test
    void probe_success_whenServerListening() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            TcpProbeResult result = probingStrategy.probe("127.0.0.1", port, 1000);

            assertThat(result.reachable()).isTrue();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(result.reason()).isEqualTo("OK");
            assertThat(result.checkedAt()).isNotNull();
        }
    }

    @Test
    void probe_connectionRefused_whenPortClosed() {
        // Tìm một port trống không ai lắng nghe
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        } catch (IOException e) {
            freePort = 59123;
        }

        TcpProbeResult result = probingStrategy.probe("127.0.0.1", freePort, 500);

        assertThat(result.reachable()).isFalse();
        assertThat(result.latencyMs()).isEqualTo(0.0);
        assertThat(result.reason()).containsIgnoringCase("refused");
    }

    @Test
    void probe_timeout_whenNonRoutableIp() {
        // 192.0.2.1 là TEST-NET-1 (RFC 5737), không bao giờ phản hồi kết nối TCP -> kích hoạt SocketTimeoutException
        TcpProbeResult result = probingStrategy.probe("192.0.2.1", 80, 200);

        assertThat(result.reachable()).isFalse();
        assertThat(result.reason()).containsIgnoringCase("timed out");
    }
}
