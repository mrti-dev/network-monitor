package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.DashboardSummaryDto;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private MetricLogRepository metricLogRepository;
    @Mock private AlertService alertService;

    private Clock fixedClock;
    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("UTC"));
        dashboardService = new DashboardServiceImpl(deviceRepository, alertRepository, metricLogRepository, alertService, fixedClock);
    }

    @Test
    void getDashboardSummary_noDevices_noMetrics_handlesCleanly() {
        when(deviceRepository.count()).thenReturn(0L);
        when(deviceRepository.countByStatus(any())).thenReturn(0L);
        when(deviceRepository.countByIsMonitoredTrue()).thenReturn(0L);

        when(alertRepository.countByStatus(AlertStatus.TRIGGERED)).thenReturn(0L);
        when(alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED)).thenReturn(0L);
        when(alertRepository.countBySeverityAndStatusIn(any(), any())).thenReturn(0L);

        when(metricLogRepository.findAverageLatencySince(any())).thenReturn(null);
        when(metricLogRepository.countTotalMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.countReachableMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.findTopDevicesWithHighestLatency(any(), any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertRepository.findTopDevicesWithMostAlerts(any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertService.getAllAlerts(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalDevices()).isEqualTo(0);
        assertThat(summary.getAverageLatencyMs()).isEqualTo(0.0);
        assertThat(summary.getReachabilityPercentage()).isEqualTo(0.0);
        assertThat(summary.getHasMetricData()).isFalse();
        assertThat(summary.getTopLatencyDevices()).isEmpty();
        assertThat(summary.getRecentAlerts()).isEmpty();
    }

    @Test
    void getDashboardSummary_noMetrics_withMonitoredDevices_hasNoMetricDataAndZeroReachability() {
        when(deviceRepository.count()).thenReturn(5L);
        when(deviceRepository.countByStatus(DeviceStatus.ONLINE)).thenReturn(5L);
        when(deviceRepository.countByIsMonitoredTrue()).thenReturn(5L);

        when(alertRepository.countByStatus(AlertStatus.TRIGGERED)).thenReturn(0L);
        when(alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED)).thenReturn(0L);
        when(alertRepository.countBySeverityAndStatusIn(any(), any())).thenReturn(0L);

        when(metricLogRepository.findAverageLatencySince(any())).thenReturn(null);
        when(metricLogRepository.countTotalMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.countReachableMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.findTopDevicesWithHighestLatency(any(), any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertRepository.findTopDevicesWithMostAlerts(any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertService.getAllAlerts(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertThat(summary.getTotalDevices()).isEqualTo(5);
        assertThat(summary.getMonitoredDevices()).isEqualTo(5);
        assertThat(summary.getHasMetricData()).isFalse();
        assertThat(summary.getReachabilityPercentage()).isEqualTo(0.0);
        assertThat(summary.getAverageLatencyMs()).isEqualTo(0.0);
    }

    @Test
    void getDashboardSummary_populatedData_computesExactStats() {
        when(deviceRepository.count()).thenReturn(10L);
        when(deviceRepository.countByStatus(DeviceStatus.ONLINE)).thenReturn(7L);
        when(deviceRepository.countByStatus(DeviceStatus.WARNING)).thenReturn(1L);
        when(deviceRepository.countByStatus(DeviceStatus.OFFLINE)).thenReturn(1L);
        when(deviceRepository.countByStatus(DeviceStatus.UNKNOWN)).thenReturn(1L);
        when(deviceRepository.countByStatus(DeviceStatus.MAINTENANCE)).thenReturn(0L);
        when(deviceRepository.countByIsMonitoredTrue()).thenReturn(8L);

        when(alertRepository.countByStatus(AlertStatus.TRIGGERED)).thenReturn(2L);
        when(alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED)).thenReturn(1L);

        when(alertRepository.countBySeverityAndStatusIn(eq(AlertSeverity.CRITICAL), any())).thenReturn(1L);
        when(alertRepository.countBySeverityAndStatusIn(eq(AlertSeverity.WARNING), any())).thenReturn(2L);
        when(alertRepository.countBySeverityAndStatusIn(eq(AlertSeverity.INFO), any())).thenReturn(0L);

        when(metricLogRepository.findAverageLatencySince(any())).thenReturn(24.5);
        when(metricLogRepository.countTotalMetricsSince(any())).thenReturn(100L);
        when(metricLogRepository.countReachableMetricsSince(any())).thenReturn(95L);

        List<Object[]> topLatRows = new ArrayList<>();
        topLatRows.add(new Object[]{1L, "Gateway", "192.168.1.1", 45.2});
        when(metricLogRepository.findTopDevicesWithHighestLatency(any(), any(Pageable.class))).thenReturn(topLatRows);

        List<Object[]> topAlertRows = new ArrayList<>();
        topAlertRows.add(new Object[]{2L, "Switch-1", "192.168.1.2", 7L});
        when(alertRepository.findTopDevicesWithMostAlerts(any(Pageable.class))).thenReturn(topAlertRows);

        AlertDto recentAlert = AlertDto.builder()
                .id(50L)
                .deviceId(2L)
                .deviceName("Switch-1")
                .alertType(AlertType.OFFLINE)
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.TRIGGERED)
                .message("Switch went down")
                .triggeredAt(LocalDateTime.now(fixedClock))
                .build();
        when(alertService.getAllAlerts(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(recentAlert)));

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertThat(summary.getTotalDevices()).isEqualTo(10);
        assertThat(summary.getOnlineDevices()).isEqualTo(7);
        assertThat(summary.getOfflineDevices()).isEqualTo(1);
        assertThat(summary.getMonitoredDevices()).isEqualTo(8);
        assertThat(summary.getTotalOpenAlerts()).isEqualTo(3);
        assertThat(summary.getCriticalAlerts()).isEqualTo(1);
        assertThat(summary.getWarningAlerts()).isEqualTo(2);
        assertThat(summary.getInfoAlerts()).isEqualTo(0);
        assertThat(summary.getAverageLatencyMs()).isEqualTo(24.5);
        // Reachability: 95/100 = 95.0%
        assertThat(summary.getReachabilityPercentage()).isEqualTo(95.0);
        assertThat(summary.getHasMetricData()).isTrue();
        assertThat(summary.getTopLatencyDevices()).hasSize(1);
        assertThat(summary.getTopAlertDevices()).hasSize(1);
        assertThat(summary.getRecentAlerts()).hasSize(1);
        assertThat(summary.getRecentAlerts().get(0).getMessage()).isEqualTo("Switch went down");
    }

    @Test
    void getDashboardSummary_usesInjectedClockFor24hWindow() {
        when(deviceRepository.count()).thenReturn(1L);
        when(deviceRepository.countByStatus(any())).thenReturn(0L);
        when(deviceRepository.countByIsMonitoredTrue()).thenReturn(0L);
        when(alertRepository.countByStatus(any())).thenReturn(0L);
        when(alertRepository.countBySeverityAndStatusIn(any(), any())).thenReturn(0L);
        when(metricLogRepository.findAverageLatencySince(any())).thenReturn(null);
        when(metricLogRepository.countTotalMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.countReachableMetricsSince(any())).thenReturn(0L);
        when(metricLogRepository.findTopDevicesWithHighestLatency(any(), any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertRepository.findTopDevicesWithMostAlerts(any(Pageable.class))).thenReturn(new ArrayList<>());
        when(alertService.getAllAlerts(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        dashboardService.getDashboardSummary();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metricLogRepository).findAverageLatencySince(captor.capture());

        LocalDateTime expectedSince = LocalDateTime.now(fixedClock).minusHours(24);
        assertThat(captor.getValue()).isEqualTo(expectedSince);
    }
}
