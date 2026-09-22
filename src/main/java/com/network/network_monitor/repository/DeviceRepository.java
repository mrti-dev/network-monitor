package com.network.network_monitor.repository;

import java.util.List;
import java.util.Optional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.DeviceStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByIpAddress(String ipAddress);

    boolean existsByIpAddress(String ipAddress);

    List<Device> findByIsMonitoredTrue();

    List<Device> findByStatus(DeviceStatus status);
}