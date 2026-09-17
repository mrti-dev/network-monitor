package com.network.network_monitor.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceIdAndStatus(Long deviceId, AlertStatus status);

    List<Alert> findByDeviceIdAndStatusIn(Long deviceId, Collection<AlertStatus> statuses);

    Optional<Alert> findFirstByDeviceIdAndAlertTypeAndStatusInOrderByIdDesc(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);

    List<Alert> findByStatus(AlertStatus status);

    long countByStatusIn(Collection<AlertStatus> statuses);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status IN :statuses")
    long countActive(@Param("statuses") Collection<AlertStatus> statuses);
}