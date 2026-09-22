package com.network.network_monitor.service;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;

public interface AlertService {
    void triggerAlert(Device device, String message, AlertType type);
    void resolveAlerts(Device device);
}
