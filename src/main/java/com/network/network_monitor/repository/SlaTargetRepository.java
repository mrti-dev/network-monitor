package com.network.network_monitor.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.network.network_monitor.entity.SlaTarget;

@Repository
public interface SlaTargetRepository extends JpaRepository<SlaTarget, Long> {
    Optional<SlaTarget> findByDeviceId(Long deviceId);
    Optional<SlaTarget> findByGroupId(Long groupId);
}
