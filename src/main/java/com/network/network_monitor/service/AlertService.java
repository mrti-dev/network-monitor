package com.network.network_monitor.service;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;

public interface AlertService {
    void triggerAlert(Device device, String message, AlertType type);
    void resolveAlerts(Device device, AlertType type);
    org.springframework.data.domain.Page<com.network.network_monitor.dto.AlertDto> getAllAlerts(org.springframework.data.domain.Pageable pageable);
}
