package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.notification.AlertCreated;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.service.AlertService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public void triggerAlert(Device device, String message, AlertType type) {
        // Kiểm tra xem đã có alert loại này đang mở chưa (TRIGGERED hoặc ACKNOWLEDGED)
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

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
            events.publishEvent(new AlertCreated(alert.getId(), alert.getMessage()));
        }
    }

    @Override
    @Transactional
    public void resolveAlerts(Device device, AlertType type) {
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

        for (Alert alert : activeAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }

    @Override
    public Page<AlertDto> getAllAlerts(Pageable pageable) {
        return alertRepository.findAllAlertsWithDevice(pageable).map(proj -> AlertDto.builder()
                .id(proj.getId())
                .deviceId(proj.getDeviceId())
                .deviceName(proj.getDeviceName())
                .isDeviceDeleted(proj.getIsDeviceDeleted())
                .alertType(parseEnum(AlertType.class, proj.getAlertType(), proj.getId()))
                .message(proj.getMessage())
                .severity(parseEnum(AlertSeverity.class, proj.getSeverity(), proj.getId()))
                .status(parseEnum(AlertStatus.class, proj.getStatus(), proj.getId()))
                .triggeredAt(proj.getTriggeredAt())
                .resolvedAt(proj.getResolvedAt())
                .build());
    }
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, Long alertId) {
        try {
            if (value == null) throw new IllegalArgumentException("null");
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            log.error("Alert ID {}: giá trị {} không hợp lệ: {}", alertId, type.getSimpleName(), value);
            throw new IllegalStateException("Dữ liệu enum không hợp lệ cho alert ID " + alertId, e);
        }
    }
}
