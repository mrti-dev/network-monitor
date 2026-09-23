package com.network.network_monitor.scheduler;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.repository.MetricLogRepository;

class MetricCleanupJobTest {
    @Test
    void cleanupUsesInjectedClockAndRetentionAndContainsFailures() {
        var repository = mock(MetricLogRepository.class);
        var defaults = new MonitoringDefaults();
        defaults.setRetentionDays(3);
        var clock = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);
        var threshold = LocalDateTime.of(2026, 9, 20, 12, 0);
        when(repository.deleteByRecordedAtBefore(threshold)).thenReturn(4).thenThrow(new IllegalStateException("database"));
        var job = new MetricCleanupJob(repository, defaults, clock);
        job.cleanupOldMetrics();
        assertThatCode(job::cleanupOldMetrics).doesNotThrowAnyException();
        verify(repository, times(2)).deleteByRecordedAtBefore(threshold);
    }
}
