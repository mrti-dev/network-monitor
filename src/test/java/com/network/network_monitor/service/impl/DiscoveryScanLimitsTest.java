package com.network.network_monitor.service.impl;

import com.network.network_monitor.config.DiscoveryLimits;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.ScanRequestDto;
import com.network.network_monitor.exception.InvalidCidrException;
import com.network.network_monitor.exception.ScanTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Kiểm thử giới hạn tài nguyên và xác thực CIDR trong DiscoveryServiceImpl.
 * Không cần DB — chỉ test logic guard trước khi tạo task.
 */
class DiscoveryScanLimitsTest {

    private DiscoveryServiceImpl service;
    private DiscoveryLimits limits;

    @BeforeEach
    void setUp() {
        DiscoveryTransactionService txService = mock(DiscoveryTransactionService.class);
        MonitoringDefaults defaults = new MonitoringDefaults();
        limits = new DiscoveryLimits();
        limits.setMaxHostsPerScan(254);
        limits.setMaxConcurrentScans(3);
        limits.setProbeConcurrency(4);
        limits.setScanTimeoutSeconds(5);
        limits.setProbeTimeoutMs(200);
        limits.setArpTimeoutMs(200);
        service = new DiscoveryServiceImpl(txService, defaults, limits);
    }

    // ------------------------------------------------------------------
    // CIDR validation — các subnet có octet > 255 bị từ chối
    // ------------------------------------------------------------------

    @Test
    void rejectsIpv4WithOctetGreaterThan255() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("192.168.1.256/24");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(InvalidCidrException.class);
    }

    @Test
    void rejectsPrefixZero() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("10.0.0.0/0");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(InvalidCidrException.class);
    }

    @Test
    void rejectsPrefixGreaterThan32() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("10.0.0.0/33");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(InvalidCidrException.class);
    }

    @Test
    void rejectsHostnameInCidr() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("localhost/24");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(InvalidCidrException.class);
    }

    // ------------------------------------------------------------------
    // Subnet too large — từ chối trước khi tạo task
    // ------------------------------------------------------------------

    @Test
    void rejectsSubnetExceeding254Hosts_slash16() {
        // /16 = 65534 hosts >> 254
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("10.0.0.0/16");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(ScanTooLargeException.class)
            .satisfies(ex -> {
                ScanTooLargeException e = (ScanTooLargeException) ex;
                org.assertj.core.api.Assertions.assertThat(e.getRequested()).isGreaterThan(254);
                org.assertj.core.api.Assertions.assertThat(e.getLimit()).isEqualTo(254);
            });
    }

    @Test
    void rejectsSubnetExceeding254Hosts_slash23() {
        // /23 = 510 hosts > 254
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("192.168.0.0/23");
        assertThatThrownBy(() -> service.scanNetwork(req))
            .isInstanceOf(ScanTooLargeException.class);
    }

    @Test
    void accepts_slash24_exactlyAtLimit() {
        // /24 = 254 hosts — at the limit; no exception from size guard
        // (probe sẽ timeout nhưng guard không ném)
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("192.168.1.0/24");
        // Chỉ kiểm tra không ném ScanTooLargeException
        // (có thể ném exception khác do network unavailable trong test env)
        try {
            service.scanNetwork(req);
        } catch (ScanTooLargeException e) {
            throw new AssertionError("/24 không nên bị từ chối bởi size guard", e);
        } catch (Exception ignored) {
            // Network errors are expected in unit test environment
        }
    }

    @Test
    void accepts_slash30_twoHosts() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("10.0.0.4/30");
        try {
            service.scanNetwork(req);
        } catch (ScanTooLargeException e) {
            throw new AssertionError("/30 không nên bị từ chối bởi size guard", e);
        } catch (Exception ignored) {}
    }

    @Test
    void accepts_slash31_rfc3021() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("192.168.0.0/31");
        try {
            service.scanNetwork(req);
        } catch (ScanTooLargeException e) {
            throw new AssertionError("/31 không nên bị từ chối bởi size guard", e);
        } catch (Exception ignored) {}
    }

    @Test
    void accepts_slash32_singleHost() {
        ScanRequestDto req = new ScanRequestDto();
        req.setSubnet("10.0.0.1/32");
        try {
            service.scanNetwork(req);
        } catch (ScanTooLargeException e) {
            throw new AssertionError("/32 không nên bị từ chối bởi size guard", e);
        } catch (Exception ignored) {}
    }
}
