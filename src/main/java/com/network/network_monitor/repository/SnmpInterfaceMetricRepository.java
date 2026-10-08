package com.network.network_monitor.repository;

import com.network.network_monitor.entity.SnmpInterfaceMetric;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SnmpInterfaceMetricRepository extends JpaRepository<SnmpInterfaceMetric, Long> {

    @Query("SELECT m FROM SnmpInterfaceMetric m WHERE m.device.id = :deviceId ORDER BY m.recordedAt DESC")
    List<SnmpInterfaceMetric> findLatestByDeviceId(@Param("deviceId") Long deviceId, Pageable pageable);

    @Query("SELECT m FROM SnmpInterfaceMetric m WHERE m.device.id = :deviceId AND m.interfaceIndex = :ifIndex ORDER BY m.recordedAt DESC")
    List<SnmpInterfaceMetric> findLatestByDeviceIdAndInterfaceIndex(
            @Param("deviceId") Long deviceId, @Param("ifIndex") int ifIndex, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SnmpInterfaceMetric m WHERE m.recordedAt < :before")
    int deleteByRecordedAtBefore(@Param("before") LocalDateTime before);
}
