package com.network.network_monitor.repository;

import com.network.network_monitor.dto.LatencyThresholdProjection;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByIpAddress(String ipAddress);

    @Query(value = "SELECT * FROM devices WHERE ip_address = :ipAddress", nativeQuery = true)
    Optional<Device> findByIpAddressIncludingDeleted(@Param("ipAddress") String ipAddress);

    @Query("select d.id as id, c.latencyThreshold as latencyThreshold from Device d left join d.monitoringConfig c where d.id = :id")
    Optional<LatencyThresholdProjection> findLatencyThreshold(Long id);

    boolean existsByIpAddress(String ipAddress);

    @EntityGraph(attributePaths = {"monitoringConfig"})
    List<Device> findByIsMonitoredTrue();

    List<Device> findByStatus(DeviceStatus status);
}
