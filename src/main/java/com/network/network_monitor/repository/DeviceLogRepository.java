package com.network.network_monitor.repository;

import java.util.List;

import com.network.network_monitor.entity.DeviceLog;
import com.network.network_monitor.enums.DeviceLogAction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {

    List<DeviceLog> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);

    List<DeviceLog> findByDeviceIdAndAction(Long deviceId, DeviceLogAction action);
}