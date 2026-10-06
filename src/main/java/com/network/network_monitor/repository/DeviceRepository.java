package com.network.network_monitor.repository;

import com.network.network_monitor.dto.LatencyThresholdProjection;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = {"monitoringConfig", "group"})
    List<Device> findByIsMonitoredTrue();

    List<Device> findByStatus(DeviceStatus status);

    long countByStatus(DeviceStatus status);

    long countByIsMonitoredTrue();

    @Query(
        value = "SELECT d FROM Device d LEFT JOIN FETCH d.group LEFT JOIN FETCH d.monitoringConfig " +
                "WHERE (:status IS NULL OR d.status = :status) " +
                "  AND (:deviceType IS NULL OR d.deviceType = :deviceType) " +
                "  AND (:monitored IS NULL OR d.isMonitored = :monitored) " +
                "  AND (:groupId IS NULL OR d.group.id = :groupId) " +
                "  AND (:location IS NULL OR LOWER(d.location) LIKE LOWER(CONCAT('%', :location, '%'))) " +
                "  AND (:keyword IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "       OR LOWER(d.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "       OR LOWER(d.macAddress) LIKE LOWER(CONCAT('%', :keyword, '%')))",
        countQuery = "SELECT count(d) FROM Device d " +
                "WHERE (:status IS NULL OR d.status = :status) " +
                "  AND (:deviceType IS NULL OR d.deviceType = :deviceType) " +
                "  AND (:monitored IS NULL OR d.isMonitored = :monitored) " +
                "  AND (:groupId IS NULL OR d.group.id = :groupId) " +
                "  AND (:location IS NULL OR LOWER(d.location) LIKE LOWER(CONCAT('%', :location, '%'))) " +
                "  AND (:keyword IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "       OR LOWER(d.ipAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "       OR LOWER(d.macAddress) LIKE LOWER(CONCAT('%', :keyword, '%')))"
    )
    Page<Device> findFilteredDevices(
            @Param("status") DeviceStatus status,
            @Param("deviceType") DeviceType deviceType,
            @Param("monitored") Boolean monitored,
            @Param("groupId") Long groupId,
            @Param("location") String location,
            @Param("keyword") String keyword,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Device d SET d.group = null WHERE d.group.id = :groupId")
    void unlinkDevicesFromGroup(@Param("groupId") Long groupId);
}
