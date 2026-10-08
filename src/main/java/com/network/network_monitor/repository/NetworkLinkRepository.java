package com.network.network_monitor.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.network.network_monitor.entity.NetworkLink;

@Repository
public interface NetworkLinkRepository extends JpaRepository<NetworkLink, Long> {

    @Query("SELECT l FROM NetworkLink l JOIN FETCH l.sourceDevice s JOIN FETCH l.targetDevice t " +
           "WHERE s.isDeleted = false AND t.isDeleted = false")
    List<NetworkLink> findActiveLinks();

    @Query("SELECT l FROM NetworkLink l WHERE (l.sourceDevice.id = :deviceId OR l.targetDevice.id = :deviceId)")
    List<NetworkLink> findLinksByDeviceId(@Param("deviceId") Long deviceId);

    @Query("SELECT l FROM NetworkLink l WHERE " +
           "(l.sourceDevice.id = :devA AND l.targetDevice.id = :devB) OR " +
           "(l.sourceDevice.id = :devB AND l.targetDevice.id = :devA)")
    Optional<NetworkLink> findLinkBetweenDevices(@Param("devA") Long devA, @Param("devB") Long devB);

    @Query("SELECT l FROM NetworkLink l WHERE " +
           "(l.sourceDevice.id = :devA AND l.targetDevice.id = :devB) OR " +
           "(l.sourceDevice.id = :devB AND l.targetDevice.id = :devA)")
    List<NetworkLink> findAllLinksBetweenDevices(@Param("devA") Long devA, @Param("devB") Long devB);

    List<NetworkLink> findByDiscoverySource(com.network.network_monitor.enums.LinkDiscoverySource discoverySource);

    boolean existsBySourceDeviceIdAndTargetDeviceId(Long sourceDeviceId, Long targetDeviceId);
}
