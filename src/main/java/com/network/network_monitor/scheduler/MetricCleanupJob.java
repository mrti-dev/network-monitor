package com.network.network_monitor.scheduler;

import java.time.LocalDateTime;
import java.time.Clock;
import com.network.network_monitor.config.MonitoringDefaults;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.repository.MetricLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MetricCleanupJob {

    private final MetricLogRepository metricLogRepository;
    private final com.network.network_monitor.repository.SnmpMetricLogRepository snmpMetricLogRepository;
    private final com.network.network_monitor.repository.SnmpInterfaceMetricRepository snmpInterfaceMetricRepository;
    private final com.network.network_monitor.repository.ServiceAvailabilityLogRepository availabilityLogRepository;

    private final MonitoringDefaults defaults;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MetricCleanupJob(MetricLogRepository metricLogRepository,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.network.network_monitor.repository.SnmpMetricLogRepository snmpMetricLogRepository,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.network.network_monitor.repository.SnmpInterfaceMetricRepository snmpInterfaceMetricRepository,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.network.network_monitor.repository.ServiceAvailabilityLogRepository availabilityLogRepository,
                            MonitoringDefaults defaults,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock) {
        this.metricLogRepository = metricLogRepository;
        this.snmpMetricLogRepository = snmpMetricLogRepository;
        this.snmpInterfaceMetricRepository = snmpInterfaceMetricRepository;
        this.availabilityLogRepository = availabilityLogRepository;
        this.defaults = defaults;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public MetricCleanupJob(MetricLogRepository metricLogRepository,
                            MonitoringDefaults defaults,
                            Clock clock) {
        this(metricLogRepository, null, null, null, defaults, clock);
    }

    @Scheduled(cron = "0 0 2 * * ?") // Chạy vào lúc 2:00 sáng mỗi ngày
    public void cleanupOldMetrics() {
        log.info("Bắt đầu dọn dẹp metric_logs cũ hơn {} ngày...", defaults.getRetentionDays());
        try {
            LocalDateTime threshold = LocalDateTime.now(clock).minusDays(defaults.getRetentionDays());
            int deletedCount = metricLogRepository.deleteByRecordedAtBefore(threshold);
            int deletedSnmpMetrics = snmpMetricLogRepository != null ? snmpMetricLogRepository.deleteByRecordedAtBefore(threshold) : 0;
            int deletedSnmpInterfaces = snmpInterfaceMetricRepository != null ? snmpInterfaceMetricRepository.deleteByRecordedAtBefore(threshold) : 0;
            int deletedAvailability = availabilityLogRepository != null ? availabilityLogRepository.deleteByRecordedAtBefore(threshold) : 0;
            log.info("Hoàn tất dọn dẹp metrics. MetricLogs: {}, SnmpMetrics: {}, SnmpInterfaces: {}, Availability: {}.",
                    deletedCount, deletedSnmpMetrics, deletedSnmpInterfaces, deletedAvailability);
        } catch (Exception e) {
            log.error("Lỗi khi dọn dẹp metric_logs", e);
        }
    }
}
