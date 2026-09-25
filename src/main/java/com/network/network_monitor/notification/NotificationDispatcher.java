package com.network.network_monitor.notification;

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
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AlertCreated event) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> notificationLogRepository.save(NotificationLog.builder()
                    .alert(alertRepository.getReferenceById(event.alertId()))
                    .channel(NotificationChannel.WEBSOCKET)
                    .message("Thông báo cảnh báo qua WebSocket: " + event.message())
                    .status("NOT_IMPLEMENTED")
                    .build()));
        } catch (RuntimeException e) {
            log.error("Lỗi notification cho alert ID {}", event.alertId(), e);
        }
    }
}
