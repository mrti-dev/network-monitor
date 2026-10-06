package com.network.network_monitor.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.network.network_monitor.entity.MaintenanceSchedule;
import com.network.network_monitor.enums.MaintenanceStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, Long> {

    List<MaintenanceSchedule> findByDeviceIdOrderByStartsAtDesc(Long deviceId);

    @Query("SELECT s FROM MaintenanceSchedule s JOIN FETCH s.device d " +
           "WHERE s.status IN (com.network.network_monitor.enums.MaintenanceStatus.SCHEDULED, com.network.network_monitor.enums.MaintenanceStatus.ACTIVE) " +
           "  AND s.endsAt > :now " +
           "ORDER BY s.startsAt ASC")
    List<MaintenanceSchedule> findUpcomingSchedules(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT s FROM MaintenanceSchedule s WHERE s.device.id = :deviceId " +
           "  AND (:excludeId IS NULL OR s.id <> :excludeId) " +
           "  AND s.status IN :statuses " +
           "  AND s.startsAt < :endsAt AND s.endsAt > :startsAt")
    List<MaintenanceSchedule> findOverlappingSchedules(
            @Param("deviceId") Long deviceId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt,
            @Param("excludeId") Long excludeId,
            @Param("statuses") Collection<MaintenanceStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MaintenanceSchedule s JOIN FETCH s.device d " +
           "WHERE s.status = com.network.network_monitor.enums.MaintenanceStatus.SCHEDULED AND s.startsAt <= :now")
    List<MaintenanceSchedule> findDueScheduledSchedules(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MaintenanceSchedule s JOIN FETCH s.device d " +
           "WHERE s.status = com.network.network_monitor.enums.MaintenanceStatus.ACTIVE AND s.endsAt <= :now")
    List<MaintenanceSchedule> findDueActiveSchedules(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM MaintenanceSchedule s " +
           "WHERE s.device.id = :deviceId AND s.id <> :excludeId " +
           "  AND s.status = com.network.network_monitor.enums.MaintenanceStatus.ACTIVE")
    long countActiveSchedulesExcluding(@Param("deviceId") Long deviceId, @Param("excludeId") Long excludeId);
}
