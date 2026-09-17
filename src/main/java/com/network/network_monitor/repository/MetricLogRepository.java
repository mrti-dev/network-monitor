package com.network.network_monitor.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.MetricLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricLogRepository extends JpaRepository<MetricLog, Long> {

    List<MetricLog> findByDeviceIdOrderByRecordedAtDesc(Long deviceId);

    List<MetricLog> findByDeviceIdAndRecordedAtAfter(Long deviceId, LocalDateTime after);

    Optional<MetricLog> findFirstByDeviceIdOrderByRecordedAtDesc(Long deviceId);
}