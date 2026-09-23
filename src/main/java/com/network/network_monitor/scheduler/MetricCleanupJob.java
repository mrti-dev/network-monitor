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
@RequiredArgsConstructor
public class MetricCleanupJob {

    private final MetricLogRepository metricLogRepository;

    private final MonitoringDefaults defaults;
    private final Clock clock;

    @Scheduled(cron = "0 0 2 * * ?") // Chạy vào lúc 2:00 sáng mỗi ngày
    public void cleanupOldMetrics() {
        log.info("Bắt đầu dọn dẹp metric_logs cũ hơn {} ngày...", defaults.getRetentionDays());
        try {
            LocalDateTime threshold = LocalDateTime.now(clock).minusDays(defaults.getRetentionDays());
            int deletedCount = metricLogRepository.deleteByRecordedAtBefore(threshold);
            log.info("Hoàn tất dọn dẹp metric_logs. Đã xóa {} bản ghi.", deletedCount);
        } catch (Exception e) {
            log.error("Lỗi khi dọn dẹp metric_logs", e);
        }
    }
}
