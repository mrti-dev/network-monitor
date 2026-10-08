package com.network.network_monitor.repository;

import com.network.network_monitor.entity.SnmpMetricLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SnmpMetricLogRepository extends JpaRepository<SnmpMetricLog, Long> {

    @Query("SELECT m FROM SnmpMetricLog m WHERE m.device.id = :deviceId ORDER BY m.recordedAt DESC")
    List<SnmpMetricLog> findLatestByDeviceId(@Param("deviceId") Long deviceId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SnmpMetricLog m WHERE m.recordedAt < :before")
    int deleteByRecordedAtBefore(@Param("before") LocalDateTime before);
}
