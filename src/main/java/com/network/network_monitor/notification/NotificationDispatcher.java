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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void dispatch(Alert alert) {
        try {
            log.info("Chuẩn bị gửi cảnh báo: [{}] Thiết bị {} - {}", alert.getSeverity(), alert.getDevice().getIpAddress(), alert.getMessage());
            
            // TODO: Gửi qua Telegram/Email/WebSocket tại đây
            // Hiện tại chưa triển khai, dùng trạng thái NOT_IMPLEMENTED
            
            NotificationLog logEntry = NotificationLog.builder()
                    .alert(alert)
                    .channel(com.network.network_monitor.enums.NotificationChannel.TELEGRAM)
                    .message("Chưa cấu hình Telegram/Email. Cảnh báo: " + alert.getMessage())
                    .status("NOT_IMPLEMENTED")
                    .sentAt(LocalDateTime.now())
                    .build();
                    
            notificationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo cho cảnh báo ID {}: {}", alert.getId(), e.getMessage(), e);
        }
    }
}
