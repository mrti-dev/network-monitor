package com.network.network_monitor.notification;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.NotificationLog;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.NotificationChannel;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        dispatcher = new NotificationDispatcher(notificationLogRepository, alertRepository, messagingTemplate, transactionManager);
    }

    @Test
    void dispatchAlertEvent_success_logsSentStatus() {
        Alert mockAlert = Alert.builder().id(10L).build();
        when(alertRepository.getReferenceById(10L)).thenReturn(mockAlert);

        AlertEvent event = new AlertEvent(10L, 1L, "Router-1", AlertType.HIGH_LATENCY, "Latency high", AlertSeverity.WARNING, AlertStatus.TRIGGERED, "TRIGGERED", LocalDateTime.now());
        dispatcher.dispatchAlertEvent(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/alerts"), eq(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"), eq(event));

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());

        NotificationLog saved = captor.getValue();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.WEBSOCKET);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getMessage()).contains("Latency high");
    }

    @Test
    void dispatchAlertEvent_whenWebSocketFails_logsFailedStatusAndDoesNotThrow() {
        Alert mockAlert = Alert.builder().id(20L).build();
        when(alertRepository.getReferenceById(20L)).thenReturn(mockAlert);
        doThrow(new MessagingException("Connection dropped")).when(messagingTemplate).convertAndSend(eq("/topic/alerts"), any(Object.class));

        AlertEvent event = new AlertEvent(20L, 2L, "Switch-1", AlertType.OFFLINE, "Alert resolved", AlertSeverity.CRITICAL, AlertStatus.RESOLVED, "RESOLVED", LocalDateTime.now());

        // Must not throw exception
        dispatcher.dispatchAlertEvent(event);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());

        NotificationLog saved = captor.getValue();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.WEBSOCKET);
        assertThat(saved.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void dispatchDeviceStatusChanged_sendsToTopics() {
        DeviceStatusChangedEvent event = new DeviceStatusChangedEvent(5L, "Router", "192.168.1.254", DeviceStatus.ONLINE, DeviceStatus.OFFLINE, "Ping timeout", LocalDateTime.now());

        dispatcher.dispatchDeviceStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/devices"), eq(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"), eq(event));
    }

    @Test
    void dispatchDeviceStatusChanged_whenWebSocketFails_doesNotThrow() {
        doThrow(new RuntimeException("Broker error")).when(messagingTemplate).convertAndSend(eq("/topic/devices"), any(Object.class));

        DeviceStatusChangedEvent event = new DeviceStatusChangedEvent(5L, "Router", "192.168.1.254", DeviceStatus.ONLINE, DeviceStatus.OFFLINE, "Ping timeout", LocalDateTime.now());

        // Must not throw
        dispatcher.dispatchDeviceStatusChanged(event);
    }

    @Test
    void dispatchTcpPortStatusChanged_sendsToPortsAndDashboardTopics() {
        com.network.network_monitor.notification.TcpPortStatusChangedEvent event =
                new com.network.network_monitor.notification.TcpPortStatusChangedEvent(
                        10L, 1L, "Router", "192.168.1.1", 80, "HTTP",
                        com.network.network_monitor.enums.TcpPortStatus.UNKNOWN,
                        com.network.network_monitor.enums.TcpPortStatus.UP,
                        15.0, "OK", LocalDateTime.now());

        dispatcher.dispatchTcpPortStatusChanged(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/ports"), eq(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"), eq(event));
    }

    @Test
    void dispatchMaintenanceSchedule_sendsToMaintenanceAndDashboardTopics() {
        MaintenanceScheduleEvent event = new MaintenanceScheduleEvent(
                1L, 2L, "Router Core", "Bảo trì",
                com.network.network_monitor.enums.MaintenanceAction.ACTIVATED,
                com.network.network_monitor.enums.MaintenanceStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                LocalDateTime.now()
        );

        dispatcher.dispatchMaintenanceSchedule(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/maintenance"), eq(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"), eq(event));
    }
}
