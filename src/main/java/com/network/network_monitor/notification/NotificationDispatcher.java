package com.network.network_monitor.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import com.network.network_monitor.entity.NotificationLog;
import com.network.network_monitor.enums.NotificationChannel;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationLogRepository notificationLogRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PlatformTransactionManager transactionManager;



    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchAlertEvent(AlertEvent event) {
        String statusToSave = "FAILED";
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/alerts", event);
                messagingTemplate.convertAndSend("/topic/dashboard", event);
                statusToSave = "SENT";
            }
        } catch (Exception e) {
            log.error("Lỗi gửi WebSocket cho alert event ID {}", event.alertId(), e);
            statusToSave = "FAILED";
        }

        saveNotificationLog(event.alertId(), event.action() + ": " + event.message(), statusToSave);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchDeviceStatusChanged(DeviceStatusChangedEvent event) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/devices", event);
                messagingTemplate.convertAndSend("/topic/dashboard", event);
            }
        } catch (Exception e) {
            log.error("Lỗi gửi WebSocket cho device status event ID {}", event.deviceId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchTcpPortStatusChanged(TcpPortStatusChangedEvent event) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/ports", event);
                messagingTemplate.convertAndSend("/topic/dashboard", event);
            }
        } catch (Exception e) {
            log.error("Lỗi gửi WebSocket cho TCP port status event monitor ID {}", event.monitorId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchMetricRecorded(MetricRecordedEvent event) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/devices/" + event.deviceId() + "/metrics", event);
            }
        } catch (Exception e) {
            log.error("Lỗi gửi WebSocket cho metric event ID {}", event.metricId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatchMaintenanceSchedule(MaintenanceScheduleEvent event) {
        try {
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/maintenance", event);
                messagingTemplate.convertAndSend("/topic/dashboard", event);
            }
        } catch (Exception e) {
            log.error("Lỗi gửi WebSocket cho maintenance schedule event ID {}", event.scheduleId(), e);
        }
    }

    private void saveNotificationLog(Long alertId, String message, String status) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(s -> notificationLogRepository.save(NotificationLog.builder()
                    .alert(alertRepository.getReferenceById(alertId))
                    .channel(NotificationChannel.WEBSOCKET)
                    .message(message)
                    .status(status)
                    .build()));
        } catch (RuntimeException e) {
            log.error("Lỗi lưu notification log cho alert ID {}", alertId, e);
        }
    }
}
