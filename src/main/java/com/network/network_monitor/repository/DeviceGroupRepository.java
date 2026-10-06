package com.network.network_monitor.repository;

import com.network.network_monitor.entity.DeviceGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
    Optional<DeviceGroup> findByName(String name);
    boolean existsByName(String name);
    List<DeviceGroup> findAllByOrderByNameAsc();
}
