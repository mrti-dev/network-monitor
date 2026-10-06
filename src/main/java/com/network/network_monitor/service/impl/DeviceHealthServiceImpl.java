package com.network.network_monitor.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.service.DeviceHealthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceHealthServiceImpl implements DeviceHealthService {

    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void processEvaluation(Long deviceId, DeviceStatus targetStatus, AlertType alertToTrigger, String triggerMessage, List<AlertType> alertsToResolve) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || device.getStatus() == DeviceStatus.MAINTENANCE) {
            return;
        }

        DeviceStatus previousStatus = device.getStatus();
        boolean statusChanged = (previousStatus != targetStatus);

        if (statusChanged) {
            device.setStatus(targetStatus);
            deviceRepository.save(device);

            String reason = (triggerMessage != null && !triggerMessage.isBlank())
                    ? triggerMessage
                    : (targetStatus == DeviceStatus.ONLINE ? "Thiết bị phản hồi ổn định" : ("Trạng thái thay đổi thành " + targetStatus));

            eventPublisher.publishEvent(new DeviceStatusChangedEvent(
                    device.getId(),
                    device.getName(),
                    device.getIpAddress(),
                    previousStatus,
                    targetStatus,
                    reason,
                    LocalDateTime.now()
            ));
        }

        if (alertsToResolve != null && !alertsToResolve.isEmpty()) {
            for (AlertType type : alertsToResolve) {
                alertService.resolveAlerts(device, type);
            }
        }

        if (alertToTrigger != null) {
            alertService.triggerAlert(device, triggerMessage, alertToTrigger);
        }
    }
}
