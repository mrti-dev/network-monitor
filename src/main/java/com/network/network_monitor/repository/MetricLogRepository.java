package com.network.network_monitor.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.network.network_monitor.entity.MetricLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MetricLogRepository extends JpaRepository<MetricLog, Long> {

    List<MetricLog> findByDeviceIdOrderByRecordedAtDesc(Long deviceId);

    @Modifying
    @Transactional
    @Query("DELETE FROM MetricLog m WHERE m.recordedAt < :before")
    void deleteByRecordedAtBefore(LocalDateTime before);
}