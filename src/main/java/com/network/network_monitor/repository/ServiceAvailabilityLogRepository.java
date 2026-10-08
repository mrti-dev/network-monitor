package com.network.network_monitor.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.ServiceAvailabilityLog;
import com.network.network_monitor.enums.ServiceType;

public interface ServiceAvailabilityLogRepository extends JpaRepository<ServiceAvailabilityLog, Long> {

    @Query("SELECT count(l), SUM(CASE WHEN l.available = true THEN 1L ELSE 0L END) " +
            "FROM ServiceAvailabilityLog l WHERE l.device.id = :deviceId AND l.serviceType = :serviceType " +
            "AND l.recordedAt >= :from AND l.recordedAt <= :to")
    Object[] aggregateAvailability(@Param("deviceId") Long deviceId,
                                   @Param("serviceType") ServiceType serviceType,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Modifying
    @Transactional
    @Query("DELETE FROM ServiceAvailabilityLog l WHERE l.recordedAt < :before")
    int deleteByRecordedAtBefore(@Param("before") LocalDateTime before);
}
