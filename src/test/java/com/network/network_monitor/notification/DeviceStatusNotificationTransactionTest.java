package com.network.network_monitor.notification;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.DeviceHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class DeviceStatusNotificationTransactionTest {

    @Autowired
    private DeviceHealthService deviceHealthService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private static final java.util.concurrent.atomic.AtomicInteger IP_COUNTER = new java.util.concurrent.atomic.AtomicInteger(1);
    private Device testDevice;

    @BeforeEach
    void setUp() {
        reset(messagingTemplate);

        int counter = IP_COUNTER.incrementAndGet();
        testDevice = Device.builder()
                .name("TxTestRouter-" + counter)
                .ipAddress("10.250.99." + counter)
                .deviceType(DeviceType.ROUTER)
                .status(DeviceStatus.UNKNOWN)
                .isMonitored(true)
                .isDeleted(false)
                .build();
        testDevice = deviceRepository.save(testDevice);
    }

    @Test
    void whenTransactionFails_shouldNotSendWebSocketMessage() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.execute(status -> {
                deviceHealthService.processEvaluation(
                        testDevice.getId(),
                        DeviceStatus.ONLINE,
                        null,
                        "Trực tuyến",
                        null
                );
                throw new RuntimeException("Cố tình gây lỗi transaction rollback");
            });
        } catch (RuntimeException ignored) {
            // Expected exception
        }

        // Kiểm tra database: transaction đã rollback nên status vẫn là UNKNOWN
        Device deviceInDb = deviceRepository.findById(testDevice.getId()).orElseThrow();
        assertThat(deviceInDb.getStatus()).isEqualTo(DeviceStatus.UNKNOWN);

        // Transaction thất bại: KHÔNG ĐƯỢC gửi WebSocket
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/devices"), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/dashboard"), any(Object.class));
    }

    @Test
    void whenTransactionSucceeds_shouldSendWebSocketMessageAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            deviceHealthService.processEvaluation(
                    testDevice.getId(),
                    DeviceStatus.ONLINE,
                    null,
                    "Trực tuyến",
                    null
            );
        });

        // Kiểm tra database: status đã chuyển ONLINE
        Device deviceInDb = deviceRepository.findById(testDevice.getId()).orElseThrow();
        assertThat(deviceInDb.getStatus()).isEqualTo(DeviceStatus.ONLINE);

        // Transaction thành công: gửi WebSocket tới /topic/devices và /topic/dashboard
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/devices"), any(Object.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/dashboard"), any(Object.class));
    }
}
