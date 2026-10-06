package com.network.network_monitor.service;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;

@SpringBootTest
class MetricRollbackTest {

    @Autowired
    private MetricRecordingService metricRecordingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private MetricLogRepository metricLogRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Device testDevice;

    @BeforeEach
    void setUp() {
        metricLogRepository.deleteAll();
        deviceRepository.deleteAll();

        String ip = "10.150." + (int)(Math.random() * 250 + 1) + "." + (int)(Math.random() * 250 + 1);
        testDevice = deviceRepository.save(Device.builder()
                .name("Metric Rollback Device")
                .ipAddress(ip)
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build());
    }

    @Test
    void transactionRollback_doesNotDispatchMetricEvent() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            metricRecordingService.recordMetric(testDevice, 15.0, 0.0, true, LocalDateTime.now());
            throw new RuntimeException("Rollback metric transaction");
        })).isInstanceOf(RuntimeException.class);

        // Do rollback, không có tin nhắn WebSocket nào được gửi đi
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices/" + testDevice.getId() + "/metrics"), any(Object.class));
    }

    @Test
    void transactionCommit_dispatchesMetricEvent() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status -> {
            metricRecordingService.recordMetric(testDevice, 20.0, 0.0, true, LocalDateTime.now());
        });

        // Transaction đã commit -> gửi WebSocket thành công
        verify(messagingTemplate).convertAndSend(eq("/topic/devices/" + testDevice.getId() + "/metrics"), any(Object.class));
    }
}
