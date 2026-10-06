package com.network.network_monitor.repository;

import com.network.network_monitor.dto.AlertProjection;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.enums.AlertType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByDeviceIdAndStatus(Long deviceId, AlertStatus status);

    List<Alert> findByStatusIn(Collection<AlertStatus> statuses);

    long countByStatus(AlertStatus status);

    long countByStatusIn(Collection<AlertStatus> statuses);

    long countBySeverity(AlertSeverity severity);

    long countBySeverityAndStatusIn(AlertSeverity severity, Collection<AlertStatus> statuses);

    Optional<Alert> findTopByDeviceIdAndAlertTypeAndStatusInOrderByTriggeredAtDesc(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);

    List<Alert> findByDeviceIdAndAlertTypeAndStatusIn(
            Long deviceId, AlertType alertType, Collection<AlertStatus> statuses);

    List<Alert> findByDeviceIdAndAlertTypeAndPortAndStatusIn(
            Long deviceId, AlertType alertType, Integer port, Collection<AlertStatus> statuses);

    @Query(
        value = "SELECT a.id as id, a.device_id as deviceId, d.name as deviceName, d.is_deleted as isDeviceDeleted, " +
                "a.alert_type as alertType, a.message as message, a.severity as severity, a.status as status, " +
                "u.username as acknowledgedBy, a.acknowledged_at as acknowledgedAt, " +
                "a.triggered_at as triggeredAt, a.resolved_at as resolvedAt " +
                "FROM alerts a JOIN devices d ON a.device_id = d.id " +
                "LEFT JOIN users u ON a.acknowledged_by = u.id " +
                "ORDER BY a.triggered_at DESC, a.id DESC",
        countQuery = "SELECT count(a.id) FROM alerts a JOIN devices d ON a.device_id = d.id",
        nativeQuery = true)
    Page<AlertProjection> findAllAlertsWithDevice(Pageable pageable);

    @Query(
        value = "SELECT a.id as id, a.device_id as deviceId, d.name as deviceName, d.is_deleted as isDeviceDeleted, " +
                "a.alert_type as alertType, a.message as message, a.severity as severity, a.status as status, " +
                "u.username as acknowledgedBy, a.acknowledged_at as acknowledgedAt, " +
                "a.triggered_at as triggeredAt, a.resolved_at as resolvedAt " +
                "FROM alerts a JOIN devices d ON a.device_id = d.id " +
                "LEFT JOIN users u ON a.acknowledged_by = u.id " +
                "WHERE (:status IS NULL OR a.status = :status) " +
                "  AND (:severity IS NULL OR a.severity = :severity) " +
                "  AND (:deviceId IS NULL OR a.device_id = :deviceId) " +
                "  AND (:keyword IS NULL OR LOWER(a.message) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.ip_address) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                "  AND (:fromDateTime IS NULL OR a.triggered_at >= :fromDateTime) " +
                "  AND (:toDateTime IS NULL OR a.triggered_at <= :toDateTime) " +
                "ORDER BY a.triggered_at DESC, a.id DESC",
        countQuery = "SELECT count(a.id) FROM alerts a JOIN devices d ON a.device_id = d.id " +
                "WHERE (:status IS NULL OR a.status = :status) " +
                "  AND (:severity IS NULL OR a.severity = :severity) " +
                "  AND (:deviceId IS NULL OR a.device_id = :deviceId) " +
                "  AND (:keyword IS NULL OR LOWER(a.message) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.ip_address) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                "  AND (:fromDateTime IS NULL OR a.triggered_at >= :fromDateTime) " +
                "  AND (:toDateTime IS NULL OR a.triggered_at <= :toDateTime)",
        nativeQuery = true)
    Page<AlertProjection> findFilteredAlerts(
            @Param("status") String status,
            @Param("severity") String severity,
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime,
            Pageable pageable);

    @Query("SELECT a.device.id as deviceId, a.device.name as deviceName, a.device.ipAddress as ipAddress, count(a.id) as alertCount " +
           "FROM Alert a WHERE a.device.isDeleted = false AND a.status IN (com.network.network_monitor.enums.AlertStatus.TRIGGERED, com.network.network_monitor.enums.AlertStatus.ACKNOWLEDGED) " +
           "GROUP BY a.device.id, a.device.name, a.device.ipAddress " +
           "ORDER BY count(a.id) DESC")
    List<Object[]> findTopDevicesWithMostAlerts(Pageable pageable);
}
