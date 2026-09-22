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
        // Kiểm tra xem đã có alert nào chưa xử lý cho thiết bị này không
        java.util.List<Alert> activeAlerts = alertRepository.findByStatusIn(java.util.List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));
        boolean hasActive = activeAlerts.stream().anyMatch(a -> a.getDevice().getId().equals(device.getId()));
                
        if (!hasActive) {
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
    public void resolveAlerts(Device device) {
        java.util.List<Alert> activeAlerts = alertRepository.findByDeviceIdAndStatus(device.getId(), AlertStatus.TRIGGERED);
        activeAlerts.addAll(alertRepository.findByDeviceIdAndStatus(device.getId(), AlertStatus.ACKNOWLEDGED));
                
        for (Alert alert : activeAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }
}
