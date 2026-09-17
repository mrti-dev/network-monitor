package com.network.network_monitor.repository;

import java.util.Optional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MonitoringConfigRepository extends JpaRepository<MonitoringConfig, Long> {

    Optional<MonitoringConfig> findByDevice(Device device);

    Optional<MonitoringConfig> findByDeviceId(Long deviceId);

    boolean existsByDeviceId(Long deviceId);
}