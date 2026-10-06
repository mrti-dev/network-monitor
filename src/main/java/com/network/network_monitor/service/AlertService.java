package com.network.network_monitor.service;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.AlertFilterDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AlertService {
    void triggerAlert(Device device, String message, AlertType type);
    void triggerPortAlert(Device device, Integer port, String serviceName, String reason);
    void resolveAlerts(Device device, AlertType type);
    void resolvePortAlert(Device device, Integer port);
    Page<AlertDto> getAllAlerts(Pageable pageable);
    Page<AlertDto> searchAlerts(AlertFilterDto filter, Pageable pageable);
    void acknowledgeAlert(Long alertId, String username);
    void resolveAlert(Long alertId);
}
