package com.network.network_monitor.repository;

import com.network.network_monitor.dto.AlertProjection;
import com.network.network_monitor.entity.*;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryMappingDataJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private MetricLogRepository metricLogRepository;

    private User adminUser;
    private DeviceGroup defaultGroup;
    private Device activeDevice;
    private Device deletedDevice;

    @BeforeEach
    void setUp() {
        adminUser = em.persistFlushFind(User.builder()
                .username("admin_test")
                .password("$2a$10$hash")
                .role(Role.ADMIN)
                .build());

        defaultGroup = em.persistFlushFind(DeviceGroup.builder()
                .name("Core Group")
                .pingInterval(30)
                .timeoutMs(1500)
                .latencyThreshold(50.0)
                .build());

        activeDevice = Device.builder()
                .name("Switch 01")
                .ipAddress("192.168.1.10")
                .macAddress("AA:BB:CC:DD:EE:01")
                .deviceType(DeviceType.SWITCH)
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .group(defaultGroup)
                .build();
        activeDevice = em.persistAndFlush(activeDevice);

        MonitoringConfig config = MonitoringConfig.builder()
                .device(activeDevice)
                .deviceId(activeDevice.getId())
                .strategyType(ProbingMethod.ICMP)
                .pingInterval(10)
                .build();
        em.persistAndFlush(config);
        activeDevice.setMonitoringConfig(config);

        deletedDevice = Device.builder()
                .name("Old Router")
                .ipAddress("192.168.1.99")
                .macAddress("AA:BB:CC:DD:EE:99")
                .deviceType(DeviceType.ROUTER)
                .status(DeviceStatus.OFFLINE)
                .isMonitored(false)
                .isDeleted(true)
                .build();
        deletedDevice = em.persistAndFlush(deletedDevice);
    }

    @Test
    void testAlert_acknowledgedAtMappingAndQuery() {
        LocalDateTime triggerTime = LocalDateTime.now().minusMinutes(30).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime ackTime = LocalDateTime.now().minusMinutes(10).truncatedTo(ChronoUnit.SECONDS);

        Alert alert = Alert.builder()
                .device(activeDevice)
                .alertType(AlertType.HIGH_LATENCY)
                .severity(AlertSeverity.WARNING)
                .status(AlertStatus.ACKNOWLEDGED)
                .message("Latency exceeded 50ms")
                .triggeredAt(triggerTime)
                .acknowledgedBy(adminUser)
                .acknowledgedAt(ackTime)
                .build();
        Alert savedAlert = em.persistFlushFind(alert);

        assertThat(savedAlert.getId()).isNotNull();
        assertThat(savedAlert.getAcknowledgedAt()).isEqualTo(ackTime);
        assertThat(savedAlert.getAcknowledgedBy()).isEqualTo(adminUser);

        // Fetch fresh from repository
        Alert reloaded = alertRepository.findById(savedAlert.getId()).orElseThrow();
        assertThat(reloaded.getAcknowledgedAt()).isNotNull();
        assertThat(reloaded.getAcknowledgedBy().getUsername()).isEqualTo("admin_test");
    }

    @Test
    void testFindFilteredAlerts_includesAlertsOfSoftDeletedDevices() {
        Alert deletedDevAlert = Alert.builder()
                .device(deletedDevice)
                .alertType(AlertType.OFFLINE)
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.TRIGGERED)
                .message("Device went offline before deletion")
                .triggeredAt(LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.SECONDS))
                .build();
        em.persistFlushFind(deletedDevAlert);

        Page<AlertProjection> results = alertRepository.findFilteredAlerts(
                null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(results.getContent()).isNotEmpty();
        boolean foundDeleted = results.getContent().stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getIsDeviceDeleted()) && "Old Router".equals(p.getDeviceName()));
        assertThat(foundDeleted).isTrue();
    }

    @Test
    void testDashboardAlertSeverity_countsOnlyOpenAlerts() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        // 1 Open CRITICAL
        em.persist(Alert.builder().device(activeDevice).alertType(AlertType.OFFLINE).severity(AlertSeverity.CRITICAL).status(AlertStatus.TRIGGERED).message("Alert 1").triggeredAt(now).build());
        // 1 Acknowledged CRITICAL
        em.persist(Alert.builder().device(activeDevice).alertType(AlertType.PACKET_LOSS).severity(AlertSeverity.CRITICAL).status(AlertStatus.ACKNOWLEDGED).message("Alert 2").triggeredAt(now).acknowledgedBy(adminUser).acknowledgedAt(now).build());
        // 1 Resolved CRITICAL (should NOT be counted)
        em.persist(Alert.builder().device(activeDevice).alertType(AlertType.HIGH_LATENCY).severity(AlertSeverity.CRITICAL).status(AlertStatus.RESOLVED).message("Alert 3").triggeredAt(now.minusHours(2)).resolvedAt(now.minusHours(1)).build());

        em.flush();

        long openCritical = alertRepository.countBySeverityAndStatusIn(AlertSeverity.CRITICAL, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));
        assertThat(openCritical).isEqualTo(2);
    }

    @Test
    void testTopDevicesWithMostAlerts_onlyCountsOpenAlerts() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        // Active device has 2 open alerts and 5 resolved alerts
        em.persist(Alert.builder().device(activeDevice).alertType(AlertType.OFFLINE).severity(AlertSeverity.CRITICAL).status(AlertStatus.TRIGGERED).message("Open 1").triggeredAt(now).build());
        em.persist(Alert.builder().device(activeDevice).alertType(AlertType.PACKET_LOSS).severity(AlertSeverity.WARNING).status(AlertStatus.ACKNOWLEDGED).message("Open 2").triggeredAt(now).build());
        for (int i = 0; i < 5; i++) {
            em.persist(Alert.builder().device(activeDevice).alertType(AlertType.HIGH_LATENCY).severity(AlertSeverity.INFO).status(AlertStatus.RESOLVED).message("Resolved " + i).triggeredAt(now.minusDays(1)).resolvedAt(now).build());
        }
        em.flush();

        List<Object[]> top = alertRepository.findTopDevicesWithMostAlerts(PageRequest.of(0, 5));
        assertThat(top).isNotEmpty();
        Object[] first = top.get(0);
        Long count = (Long) first[3];
        assertThat(count).isEqualTo(2L); // only open alerts
    }

    @Test
    void testUnlinkDevicesFromGroup_bulkModifyingQuery() {
        assertThat(activeDevice.getGroup()).isNotNull();

        deviceRepository.unlinkDevicesFromGroup(defaultGroup.getId());
        em.clear(); // clear L1 cache to force re-fetch

        Device refreshed = deviceRepository.findById(activeDevice.getId()).orElseThrow();
        assertThat(refreshed.getGroup()).isNull();
    }

    @Test
    void testDeviceFiltering_activeOnlyByDefault() {
        Page<Device> activeOnly = deviceRepository.findFilteredDevices(null, null, null, null, null, "Switch", PageRequest.of(0, 10));
        assertThat(activeOnly.getContent()).hasSize(1);
        assertThat(activeOnly.getContent().get(0).getName()).isEqualTo("Switch 01");

        // Deleted device should not appear in active search
        Page<Device> searchDeleted = deviceRepository.findFilteredDevices(null, null, null, null, null, "Old Router", PageRequest.of(0, 10));
        assertThat(searchDeleted.getContent()).isEmpty();
    }

    @Test
    void testMetricLogRepository_aggregates() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        em.persist(MetricLog.builder().device(activeDevice).latencyMs(20.0).isReachable(true).recordedAt(now.minusMinutes(5)).build());
        em.persist(MetricLog.builder().device(activeDevice).latencyMs(40.0).isReachable(true).recordedAt(now.minusMinutes(2)).build());
        em.persist(MetricLog.builder().device(activeDevice).latencyMs(null).isReachable(false).recordedAt(now.minusMinutes(1)).build());
        em.flush();

        Double avg = metricLogRepository.findAverageLatencySince(now.minusMinutes(10));
        assertThat(avg).isEqualTo(30.0);

        long total = metricLogRepository.countTotalMetricsSince(now.minusMinutes(10));
        long reachable = metricLogRepository.countReachableMetricsSince(now.minusMinutes(10));
        assertThat(total).isEqualTo(3);
        assertThat(reachable).isEqualTo(2);
    }
}
