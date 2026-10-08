package com.network.network_monitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.network.network_monitor.entity.TcpPortMonitor;

@Repository
public interface TcpPortMonitorRepository extends JpaRepository<TcpPortMonitor, Long> {

    List<TcpPortMonitor> findByDeviceIdOrderByPortAsc(Long deviceId);

    List<TcpPortMonitor> findByDeviceId(Long deviceId);

    Optional<TcpPortMonitor> findByDeviceIdAndPort(Long deviceId, Integer port);

    boolean existsByDeviceIdAndPort(Long deviceId, Integer port);

    boolean existsByDeviceIdAndPortAndIdNot(Long deviceId, Integer port, Long id);

    @Query("SELECT p FROM TcpPortMonitor p JOIN FETCH p.device d WHERE p.enabled = true AND d.isMonitored = true AND d.status <> com.network.network_monitor.enums.DeviceStatus.MAINTENANCE")
    List<TcpPortMonitor> findActiveMonitoredPorts();
}
