package com.network.network_monitor.repository;

import com.network.network_monitor.entity.SnmpConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnmpConfigRepository extends JpaRepository<SnmpConfig, Long> {

    Optional<SnmpConfig> findByDeviceId(Long deviceId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM SnmpConfig c JOIN FETCH c.device d WHERE c.enabled = true AND d.isMonitored = true AND d.isDeleted = false AND d.status <> com.network.network_monitor.enums.DeviceStatus.MAINTENANCE")
    List<SnmpConfig> findActiveConfigs();

    boolean existsByDeviceId(Long deviceId);

    void deleteByDeviceId(Long deviceId);
}
