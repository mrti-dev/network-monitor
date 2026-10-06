package com.network.network_monitor.service.impl;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.TcpPortStatus;
import com.network.network_monitor.notification.TcpPortStatusChangedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.TcpPortMonitorRepository;

@SpringBootTest
class TcpPortRollbackTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TcpPortMonitorRepository portRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private Device testDevice;
    private TcpPortMonitor testMonitor;

    @BeforeEach
    void setUp() {
        portRepository.deleteAll();
        deviceRepository.deleteAll();

        String ip = "10.200." + (int)(Math.random() * 250 + 1) + "." + (int)(Math.random() * 250 + 1);
        testDevice = deviceRepository.save(Device.builder()
                .name("Rollback Test Device")
                .ipAddress(ip)
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build());

        testMonitor = portRepository.save(TcpPortMonitor.builder()
                .device(testDevice)
                .port(8080)
                .serviceName("Test Port")
                .status(TcpPortStatus.UNKNOWN)
                .build());
    }

    @Test
    void transactionRollback_doesNotSendWebSocketMessage() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            TcpPortStatusChangedEvent event = new TcpPortStatusChangedEvent(
                    testMonitor.getId(),
                    testDevice.getId(),
                    testDevice.getName(),
                    testDevice.getIpAddress(),
                    8080,
                    "Test Port",
                    TcpPortStatus.UNKNOWN,
                    TcpPortStatus.DOWN,
                    0.0,
                    "Connection refused",
                    LocalDateTime.now()
            );
            eventPublisher.publishEvent(event);
            // Cố ý gây rollback
            throw new RuntimeException("Simulated transaction rollback");
        })).isInstanceOf(RuntimeException.class);

        // Do transaction bị rollback, AFTER_COMMIT listener KHÔNG được kích hoạt
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/ports"), any(Object.class));
    }

    @Test
    void transactionCommit_sendsWebSocketMessage() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        txTemplate.executeWithoutResult(status -> {
            TcpPortStatusChangedEvent event = new TcpPortStatusChangedEvent(
                    testMonitor.getId(),
                    testDevice.getId(),
                    testDevice.getName(),
                    testDevice.getIpAddress(),
                    8080,
                    "Test Port",
                    TcpPortStatus.UNKNOWN,
                    TcpPortStatus.UP,
                    5.0,
                    "OK",
                    LocalDateTime.now()
            );
            eventPublisher.publishEvent(event);
        });

        // Transaction đã commit -> AFTER_COMMIT listener gửi message
        verify(messagingTemplate).convertAndSend(eq("/topic/ports"), any(Object.class));
    }
}
