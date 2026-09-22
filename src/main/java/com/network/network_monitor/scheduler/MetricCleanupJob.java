package com.network.network_monitor.scheduler;

import java.time.LocalDateTime;

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

    @Scheduled(cron = "0 0 2 * * ?") // Chạy vào lúc 2:00 sáng mỗi ngày
    public void cleanupOldMetrics() {
        log.info("Bắt đầu dọn dẹp metric_logs cũ hơn 3 ngày...");
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(3);
            metricLogRepository.deleteByRecordedAtBefore(threshold);
            log.info("Hoàn tất dọn dẹp metric_logs.");
        } catch (Exception e) {
            log.error("Lỗi khi dọn dẹp metric_logs", e);
        }
    }
}
