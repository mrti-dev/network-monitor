package com.network.network_monitor.repository;

import com.network.network_monitor.dto.AlertProjection;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceIdAndStatus(Long deviceId, AlertStatus status);

    List<Alert> findByStatusIn(Collection<AlertStatus> statuses);

    Optional<Alert> findTopByDeviceIdAndAlertTypeAndStatusInOrderByTriggeredAtDesc(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);

    List<Alert> findByDeviceIdAndAlertTypeAndStatusIn(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);

    @Query(
        value = "SELECT a.id as id, a.device_id as deviceId, d.name as deviceName, d.is_deleted as isDeviceDeleted, " +
                "a.alert_type as alertType, a.message as message, a.severity as severity, a.status as status, " +
                "a.triggered_at as triggeredAt, a.resolved_at as resolvedAt " +
                "FROM alerts a JOIN devices d ON a.device_id = d.id ORDER BY a.triggered_at DESC, a.id DESC",
        countQuery = "SELECT count(a.id) FROM alerts a JOIN devices d ON a.device_id = d.id",
        nativeQuery = true)
    Page<AlertProjection> findAllAlertsWithDevice(Pageable pageable);
}
