package com.network.network_monitor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.network.network_monitor.dto.MaintenanceScheduleFormDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.MaintenanceScheduleService;

@SpringBootTest
class MaintenanceScheduleNotificationTransactionTest {

    @Autowired
    private MaintenanceScheduleService maintenanceScheduleService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private static final java.util.concurrent.atomic.AtomicInteger IP_COUNTER = new java.util.concurrent.atomic.AtomicInteger(50);
    private Device testDevice;

    @BeforeEach
    void setUp() {
        reset(messagingTemplate);

        int counter = IP_COUNTER.incrementAndGet();
        testDevice = Device.builder()
                .name("TxMaintDevice-" + counter)
                .ipAddress("10.251.99." + counter)
                .deviceType(DeviceType.ROUTER)
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build();
        testDevice = deviceRepository.save(testDevice);
    }

    @Test
    void whenTransactionFails_shouldNotSendMaintenanceWebSocketMessage() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.execute(status -> {
                maintenanceScheduleService.createSchedule(MaintenanceScheduleFormDto.builder()
                        .deviceId(testDevice.getId())
                        .title("Lịch lỗi rollback")
                        .startsAt(LocalDateTime.now().plusHours(1))
                        .endsAt(LocalDateTime.now().plusHours(2))
                        .build(), "admin");
                throw new RuntimeException("Cố tình rollback transaction maintenance");
            });
        } catch (RuntimeException ignored) {
            // Expected
        }

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/maintenance"), any(Object.class));
    }

    @Test
    void whenTransactionSucceeds_shouldSendMaintenanceWebSocketMessageAfterCommit() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            maintenanceScheduleService.createSchedule(MaintenanceScheduleFormDto.builder()
                    .deviceId(testDevice.getId())
                    .title("Lịch thành công")
                    .startsAt(LocalDateTime.now().plusHours(1))
                    .endsAt(LocalDateTime.now().plusHours(2))
                    .build(), "admin");
        });

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/maintenance"), any(Object.class));
    }
}
