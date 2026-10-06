package com.network.network_monitor.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.notification.MetricRecordedEvent;
import com.network.network_monitor.repository.MetricLogRepository;

@ExtendWith(MockitoExtension.class)
class MetricRecordingServiceTest {

    @Mock
    private MetricLogRepository metricLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MetricRecordingService metricRecordingService;

    @BeforeEach
    void setUp() {
        metricRecordingService = new MetricRecordingService(metricLogRepository, eventPublisher);
    }

    @Test
    void recordMetric_savesMetricAndPublishesEvent_withCorrectPayload() {
        Device device = Device.builder().id(5L).ipAddress("192.168.1.50").build();
        LocalDateTime now = LocalDateTime.now();

        MetricLog savedMock = MetricLog.builder()
                .id(100L)
                .device(device)
                .latencyMs(24.5)
                .packetLoss(0.0)
                .isReachable(true)
                .recordedAt(now)
                .build();

        when(metricLogRepository.save(any(MetricLog.class))).thenReturn(savedMock);

        MetricLog result = metricRecordingService.recordMetric(device, 24.5, 0.0, true, now);

        assertThat(result.getId()).isEqualTo(100L);
        verify(metricLogRepository).save(any(MetricLog.class));

        ArgumentCaptor<MetricRecordedEvent> captor = ArgumentCaptor.forClass(MetricRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        MetricRecordedEvent event = captor.getValue();
        assertThat(event.metricId()).isEqualTo(100L);
        assertThat(event.deviceId()).isEqualTo(5L);
        assertThat(event.latencyMs()).isEqualTo(24.5);
        assertThat(event.packetLoss()).isEqualTo(0.0);
        assertThat(event.reachable()).isTrue();
        assertThat(event.recordedAt()).isEqualTo(now);
    }
}
