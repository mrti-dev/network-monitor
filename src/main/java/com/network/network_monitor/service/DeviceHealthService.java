package com.network.network_monitor.service;

import java.util.List;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;

public interface DeviceHealthService {
    void processEvaluation(Long deviceId, DeviceStatus targetStatus, AlertType alertToTrigger, String triggerMessage, List<AlertType> alertsToResolve);
}
