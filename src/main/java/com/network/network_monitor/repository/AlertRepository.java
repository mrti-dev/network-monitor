package com.network.network_monitor.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceIdAndStatus(Long deviceId, AlertStatus status);

    List<Alert> findByStatusIn(Collection<AlertStatus> statuses);

    Optional<Alert> findTopByDeviceIdAndAlertTypeAndStatusInOrderByTriggeredAtDesc(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);
}