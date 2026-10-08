package com.network.network_monitor.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.network.network_monitor.entity.MetricLog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MetricLogRepository extends JpaRepository<MetricLog, Long> {

    List<MetricLog> findTop30ByDeviceIdOrderByRecordedAtDescIdDesc(Long deviceId);

    @Query("SELECT new com.network.network_monitor.dto.MetricLogDto(m.recordedAt, m.latencyMs, m.packetLoss, m.isReachable) " +
           "FROM MetricLog m WHERE m.device.id = :deviceId AND m.recordedAt >= :since " +
           "ORDER BY m.recordedAt DESC")
    List<com.network.network_monitor.dto.MetricLogDto> findLatestMetricsByDeviceIdAndSince(
            @Param("deviceId") Long deviceId,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM MetricLog m WHERE m.recordedAt < :before")
    int deleteByRecordedAtBefore(LocalDateTime before);

    @Query("SELECT AVG(m.latencyMs) FROM MetricLog m WHERE m.isReachable = true AND m.recordedAt >= :since")
    Double findAverageLatencySince(@Param("since") LocalDateTime since);

    @Query("SELECT count(m) FROM MetricLog m WHERE m.recordedAt >= :since")
    Long countTotalMetricsSince(@Param("since") LocalDateTime since);

    @Query("SELECT count(m) FROM MetricLog m WHERE m.isReachable = true AND m.recordedAt >= :since")
    Long countReachableMetricsSince(@Param("since") LocalDateTime since);

    @Query("SELECT m.device.id as deviceId, m.device.name as deviceName, m.device.ipAddress as ipAddress, AVG(m.latencyMs) as avgLatency " +
           "FROM MetricLog m WHERE m.isReachable = true AND m.recordedAt >= :since AND m.device.isDeleted = false " +
           "GROUP BY m.device.id, m.device.name, m.device.ipAddress " +
           "ORDER BY AVG(m.latencyMs) DESC")
    List<Object[]> findTopDevicesWithHighestLatency(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT m.device.id, count(m.id), " +
           "SUM(CASE WHEN m.isReachable = true THEN 1L ELSE 0L END), " +
           "AVG(CASE WHEN m.isReachable = true THEN m.latencyMs ELSE null END), " +
           "MAX(CASE WHEN m.isReachable = true THEN m.latencyMs ELSE null END), " +
           "AVG(m.packetLoss) " +
           "FROM MetricLog m WHERE m.recordedAt >= :from AND m.recordedAt <= :to " +
           "GROUP BY m.device.id")
    List<Object[]> aggregateMetricsByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT m.device.id, count(m.id) " +
           "FROM MetricLog m WHERE m.isReachable = true AND m.latencyMs <= :threshold AND m.recordedAt >= :from AND m.recordedAt <= :to " +
           "GROUP BY m.device.id")
    List<Object[]> countLatencyCompliantByPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("threshold") Double threshold);

    @Query("SELECT count(m.id) " +
           "FROM MetricLog m WHERE m.device.id = :deviceId AND m.isReachable = true AND m.latencyMs <= :threshold AND m.recordedAt >= :from AND m.recordedAt <= :to")
    Long countLatencyCompliantForDevice(@Param("deviceId") Long deviceId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("threshold") Double threshold);

    @Query("SELECT count(m.id) FROM MetricLog m WHERE m.device.id = :deviceId " +
           "AND m.packetLoss <= :threshold AND m.recordedAt >= :from AND m.recordedAt <= :to")
    Long countPacketLossCompliantForDevice(@Param("deviceId") Long deviceId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("threshold") Double threshold);
}
