package com.network.network_monitor.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
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

    @Override
    @Transactional
    public void processEvaluation(Long deviceId, DeviceStatus targetStatus, AlertType alertToTrigger, String triggerMessage, List<AlertType> alertsToResolve) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || device.getStatus() == DeviceStatus.MAINTENANCE) {
            return;
        }

        boolean statusChanged = (device.getStatus() != targetStatus);

        if (statusChanged) {
            device.setStatus(targetStatus);
            deviceRepository.save(device);
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
