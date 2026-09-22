package com.network.network_monitor.notification;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.NotificationLog;
import com.network.network_monitor.repository.NotificationLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationLogRepository notificationLogRepository;

    @Transactional
    public void dispatch(Alert alert) {
        log.info("Gửi cảnh báo: [{}] Thiết bị {} - {}", alert.getSeverity(), alert.getDevice().getIpAddress(), alert.getMessage());
        
        // TODO: Gửi qua Telegram/Email/WebSocket tại đây
        
        NotificationLog logEntry = NotificationLog.builder()
                .alert(alert)
                .channel(com.network.network_monitor.enums.NotificationChannel.TELEGRAM)
                .message("Đã gửi cảnh báo: " + alert.getMessage())
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .build();
                
        notificationLogRepository.save(logEntry);
    }
}
