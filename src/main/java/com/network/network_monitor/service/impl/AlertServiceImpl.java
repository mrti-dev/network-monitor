package com.network.network_monitor.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.notification.NotificationDispatcher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    @Transactional
    public void triggerAlert(Device device, String message, AlertType type) {
        // Kiểm tra xem đã có alert loại này đang mở chưa (TRIGGERED hoặc ACKNOWLEDGED)
        java.util.List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, java.util.List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));
                
        if (activeAlerts.isEmpty()) {
            Alert alert = Alert.builder()
                    .device(device)
                    .alertType(type)
                    .message(message)
                    .severity(AlertSeverity.CRITICAL)
                    .status(AlertStatus.TRIGGERED)
                    .triggeredAt(LocalDateTime.now())
                    .build();
            alertRepository.save(alert);
            notificationDispatcher.dispatch(alert);
        }
    }

    @Override
    @Transactional
    public void resolveAlerts(Device device, AlertType type) {
        java.util.List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, java.util.List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));
                
        for (Alert alert : activeAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }
}
