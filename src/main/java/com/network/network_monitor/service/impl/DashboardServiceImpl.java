package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.DashboardSummaryDto;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.service.DashboardService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final MetricLogRepository metricLogRepository;
    private final AlertService alertService;
    private final Clock clock;

    @Autowired
    public DashboardServiceImpl(DeviceRepository deviceRepository,
                                AlertRepository alertRepository,
                                MetricLogRepository metricLogRepository,
                                AlertService alertService,
                                @Autowired(required = false) Clock clock) {
        this.deviceRepository = deviceRepository;
        this.alertRepository = alertRepository;
        this.metricLogRepository = metricLogRepository;
        this.alertService = alertService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public DashboardServiceImpl(DeviceRepository deviceRepository,
                                AlertRepository alertRepository,
                                MetricLogRepository metricLogRepository,
                                AlertService alertService) {
        this(deviceRepository, alertRepository, metricLogRepository, alertService, Clock.systemDefaultZone());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        long totalDevices = deviceRepository.count();
        long onlineDevices = deviceRepository.countByStatus(DeviceStatus.ONLINE);
        long warningDevices = deviceRepository.countByStatus(DeviceStatus.WARNING);
        long offlineDevices = deviceRepository.countByStatus(DeviceStatus.OFFLINE);
        long unknownDevices = deviceRepository.countByStatus(DeviceStatus.UNKNOWN);
        long maintenanceDevices = deviceRepository.countByStatus(DeviceStatus.MAINTENANCE);
        long monitoredDevices = deviceRepository.countByIsMonitoredTrue();

        long triggeredAlerts = alertRepository.countByStatus(AlertStatus.TRIGGERED);
        long acknowledgedAlerts = alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED);
        long resolvedAlerts = alertRepository.countByStatus(AlertStatus.RESOLVED);
        long totalOpenAlerts = triggeredAlerts + acknowledgedAlerts;

        List<AlertStatus> openStatuses = List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED);
        long criticalAlerts = alertRepository.countBySeverityAndStatusIn(AlertSeverity.CRITICAL, openStatuses);
        long warningAlerts = alertRepository.countBySeverityAndStatusIn(AlertSeverity.WARNING, openStatuses);
        long infoAlerts = alertRepository.countBySeverityAndStatusIn(AlertSeverity.INFO, openStatuses);

        LocalDateTime since24h = LocalDateTime.now(clock).minusHours(24);
        Double avgLatency = metricLogRepository.findAverageLatencySince(since24h);
        double averageLatencyMs = (avgLatency != null) ? Math.round(avgLatency * 10.0) / 10.0 : 0.0;

        Long totalMetrics = metricLogRepository.countTotalMetricsSince(since24h);
        Long reachableMetrics = metricLogRepository.countReachableMetricsSince(since24h);
        boolean hasMetricData = false;
        double reachabilityPercentage = 0.0;

        if (totalMetrics != null && totalMetrics > 0 && reachableMetrics != null) {
            hasMetricData = true;
            reachabilityPercentage = Math.round(((double) reachableMetrics / totalMetrics) * 1000.0) / 10.0;
        } else {
            hasMetricData = false;
            reachabilityPercentage = 0.0;
            averageLatencyMs = 0.0;
        }

        // Top latency devices
        List<DashboardSummaryDto.TopDeviceLatencyDto> topLatencyDevices = new ArrayList<>();
        List<Object[]> rawLatency = metricLogRepository.findTopDevicesWithHighestLatency(since24h, PageRequest.of(0, 5));
        if (rawLatency != null) {
            for (Object[] row : rawLatency) {
                Long devId = (Long) row[0];
                String devName = (String) row[1];
                String devIp = (String) row[2];
                Double lat = (Double) row[3];
                topLatencyDevices.add(DashboardSummaryDto.TopDeviceLatencyDto.builder()
                        .deviceId(devId)
                        .deviceName(devName != null ? devName : devIp)
                        .ipAddress(devIp)
                        .averageLatencyMs(lat != null ? Math.round(lat * 10.0) / 10.0 : 0.0)
                        .build());
            }
        }

        // Top alert devices (chỉ tính alert đang mở)
        List<DashboardSummaryDto.TopDeviceAlertDto> topAlertDevices = new ArrayList<>();
        List<Object[]> rawAlerts = alertRepository.findTopDevicesWithMostAlerts(PageRequest.of(0, 5));
        if (rawAlerts != null) {
            for (Object[] row : rawAlerts) {
                Long devId = (Long) row[0];
                String devName = (String) row[1];
                String devIp = (String) row[2];
                Long count = (Long) row[3];
                topAlertDevices.add(DashboardSummaryDto.TopDeviceAlertDto.builder()
                        .deviceId(devId)
                        .deviceName(devName != null ? devName : devIp)
                        .ipAddress(devIp)
                        .alertCount(count != null ? count : 0L)
                        .build());
            }
        }

        // Recent alerts directly from alerts table
        List<AlertDto> recentAlerts = alertService.getAllAlerts(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "triggeredAt", "id"))).getContent();

        return DashboardSummaryDto.builder()
                .totalDevices(totalDevices)
                .onlineDevices(onlineDevices)
                .warningDevices(warningDevices)
                .offlineDevices(offlineDevices)
                .unknownDevices(unknownDevices)
                .maintenanceDevices(maintenanceDevices)
                .monitoredDevices(monitoredDevices)
                .totalOpenAlerts(totalOpenAlerts)
                .triggeredAlerts(triggeredAlerts)
                .acknowledgedAlerts(acknowledgedAlerts)
                .resolvedAlerts(resolvedAlerts)
                .criticalAlerts(criticalAlerts)
                .warningAlerts(warningAlerts)
                .infoAlerts(infoAlerts)
                .averageLatencyMs(averageLatencyMs)
                .reachabilityPercentage(reachabilityPercentage)
                .hasMetricData(hasMetricData)
                .topLatencyDevices(topLatencyDevices)
                .topAlertDevices(topAlertDevices)
                .recentAlerts(recentAlerts)
                .build();
    }
}
