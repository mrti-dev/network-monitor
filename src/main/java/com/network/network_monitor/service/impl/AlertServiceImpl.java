package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.AlertFilterDto;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.notification.AlertEvent;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.UserRepository;
import com.network.network_monitor.service.AlertService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    private final Clock clock;

    @Autowired
    public AlertServiceImpl(AlertRepository alertRepository,
                            UserRepository userRepository,
                            ApplicationEventPublisher events,
                            @Autowired(required = false) Clock clock) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public AlertServiceImpl(AlertRepository alertRepository, UserRepository userRepository, ApplicationEventPublisher events) {
        this(alertRepository, userRepository, events, Clock.systemDefaultZone());
    }

    public AlertServiceImpl(AlertRepository alertRepository, ApplicationEventPublisher events) {
        this(alertRepository, null, events, Clock.systemDefaultZone());
    }

    @Override
    @Transactional
    public void triggerAlert(Device device, String message, AlertType type) {
        // Kiểm tra xem đã có alert loại này đang mở chưa (TRIGGERED hoặc ACKNOWLEDGED)
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

        if (activeAlerts.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(clock);
            Alert alert = Alert.builder()
                    .device(device)
                    .alertType(type)
                    .message(message)
                    .severity(AlertSeverity.CRITICAL)
                    .status(AlertStatus.TRIGGERED)
                    .triggeredAt(now)
                    .build();
            alertRepository.save(alert);
            events.publishEvent(new AlertEvent(
                    alert.getId(),
                    device.getId(),
                    device.getName(),
                    alert.getAlertType(),
                    alert.getMessage(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    "TRIGGERED",
                    alert.getTriggeredAt()
            ));
        }
    }

    @Override
    @Transactional
    public void triggerPortAlert(Device device, Integer port, String serviceName, String reason) {
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndPortAndStatusIn(
                device.getId(), AlertType.PORT_DOWN, port, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

        if (activeAlerts.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(clock);
            String svc = (serviceName != null && !serviceName.isBlank()) ? serviceName : "TCP";
            String rsn = (reason != null && !reason.isBlank()) ? reason : "Không phản hồi";
            String msg = String.format("Dịch vụ %s tại cổng %d của thiết bị %s (%s) ngừng phản hồi: %s",
                    svc, port, device.getName(), device.getIpAddress(), rsn);

            Alert alert = Alert.builder()
                    .device(device)
                    .port(port)
                    .alertType(AlertType.PORT_DOWN)
                    .message(msg)
                    .severity(AlertSeverity.CRITICAL)
                    .status(AlertStatus.TRIGGERED)
                    .triggeredAt(now)
                    .build();
            alertRepository.save(alert);
            events.publishEvent(new AlertEvent(
                    alert.getId(),
                    device.getId(),
                    device.getName(),
                    alert.getAlertType(),
                    alert.getMessage(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    "TRIGGERED",
                    alert.getTriggeredAt()
            ));
        }
    }

    @Override
    @Transactional
    public void resolveAlerts(Device device, AlertType type) {
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndStatusIn(
                device.getId(), type, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

        LocalDateTime now = LocalDateTime.now(clock);
        for (Alert alert : activeAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alertRepository.save(alert);
            events.publishEvent(new AlertEvent(
                    alert.getId(),
                    device.getId(),
                    device.getName(),
                    alert.getAlertType(),
                    alert.getMessage(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    "RESOLVED",
                    alert.getResolvedAt()
            ));
        }
    }

    @Override
    @Transactional
    public void resolvePortAlert(Device device, Integer port) {
        List<Alert> activeAlerts = alertRepository.findByDeviceIdAndAlertTypeAndPortAndStatusIn(
                device.getId(), AlertType.PORT_DOWN, port, List.of(AlertStatus.TRIGGERED, AlertStatus.ACKNOWLEDGED));

        LocalDateTime now = LocalDateTime.now(clock);
        for (Alert alert : activeAlerts) {
            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(now);
            alertRepository.save(alert);
            events.publishEvent(new AlertEvent(
                    alert.getId(),
                    device.getId(),
                    device.getName(),
                    alert.getAlertType(),
                    alert.getMessage(),
                    alert.getSeverity(),
                    alert.getStatus(),
                    "RESOLVED",
                    alert.getResolvedAt()
            ));
        }
    }

    @Override
    @Transactional
    public void acknowledgeAlert(Long alertId, String username) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảnh báo với ID: " + alertId));

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new IllegalStateException("Không thể xác nhận cảnh báo đã ở trạng thái RESOLVED.");
        }

        if (alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
            // Thao tác idempotent: không đổi acknowledgedBy/At, không phát lại event
            return;
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now(clock));
        if (username != null && !username.isBlank() && userRepository != null) {
            userRepository.findByUsername(username).ifPresent(alert::setAcknowledgedBy);
        }
        alertRepository.save(alert);

        events.publishEvent(new AlertEvent(
                alert.getId(),
                alert.getDevice() != null ? alert.getDevice().getId() : null,
                alert.getDevice() != null ? alert.getDevice().getName() : null,
                alert.getAlertType(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getStatus(),
                "ACKNOWLEDGED",
                alert.getAcknowledgedAt()
        ));
    }

    @Override
    @Transactional
    public void resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảnh báo với ID: " + alertId));

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            // Thao tác idempotent: không đổi resolvedAt, không phát lại event
            return;
        }

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now(clock));
        alertRepository.save(alert);

        events.publishEvent(new AlertEvent(
                alert.getId(),
                alert.getDevice() != null ? alert.getDevice().getId() : null,
                alert.getDevice() != null ? alert.getDevice().getName() : null,
                alert.getAlertType(),
                alert.getMessage(),
                alert.getSeverity(),
                alert.getStatus(),
                "RESOLVED",
                alert.getResolvedAt()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertDto> getAllAlerts(Pageable pageable) {
        return alertRepository.findAllAlertsWithDevice(pageable).map(this::mapProjectionToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertDto> searchAlerts(AlertFilterDto filter, Pageable pageable) {
        if (filter == null) {
            return getAllAlerts(pageable);
        }

        String statusStr = filter.getStatus() != null ? filter.getStatus().name() : null;
        String severityStr = filter.getSeverity() != null ? filter.getSeverity().name() : null;
        String keyword = (filter.getKeyword() != null && !filter.getKeyword().isBlank()) ? filter.getKeyword().trim() : null;
        LocalDateTime fromDateTime = filter.getFromDate() != null ? filter.getFromDate().atStartOfDay() : null;
        LocalDateTime toDateTime = filter.getToDate() != null ? filter.getToDate().atTime(23, 59, 59) : null;

        return alertRepository.findFilteredAlerts(
                statusStr,
                severityStr,
                filter.getDeviceId(),
                keyword,
                fromDateTime,
                toDateTime,
                pageable
        ).map(this::mapProjectionToDto);
    }

    private AlertDto mapProjectionToDto(com.network.network_monitor.dto.AlertProjection proj) {
        return AlertDto.builder()
                .id(proj.getId())
                .deviceId(proj.getDeviceId())
                .deviceName(proj.getDeviceName())
                .isDeviceDeleted(proj.getIsDeviceDeleted())
                .alertType(parseEnum(AlertType.class, proj.getAlertType(), proj.getId()))
                .message(proj.getMessage())
                .severity(parseEnum(AlertSeverity.class, proj.getSeverity(), proj.getId()))
                .status(parseEnum(AlertStatus.class, proj.getStatus(), proj.getId()))
                .acknowledgedBy(proj.getAcknowledgedBy())
                .acknowledgedAt(proj.getAcknowledgedAt())
                .triggeredAt(proj.getTriggeredAt())
                .resolvedAt(proj.getResolvedAt())
                .build();
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
