package com.network.network_monitor.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.network.network_monitor.entity.DeviceStatusHistory;

@Repository
public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, Long> {

    @Query("SELECT h FROM DeviceStatusHistory h WHERE h.device.id = :deviceId AND h.endedAt IS NULL")
    Optional<DeviceStatusHistory> findOpenStatusByDeviceId(@Param("deviceId") Long deviceId);

    @Query("SELECT h FROM DeviceStatusHistory h WHERE h.device.id = :deviceId " +
           "AND h.startedAt < :end " +
           "AND (h.endedAt IS NULL OR h.endedAt > :start) " +
           "ORDER BY h.startedAt ASC")
    List<DeviceStatusHistory> findOverlappingHistory(
            @Param("deviceId") Long deviceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    List<DeviceStatusHistory> findByDeviceIdOrderByStartedAtAsc(Long deviceId);

    void deleteByDeviceId(Long deviceId);
}
