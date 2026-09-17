package com.network.network_monitor.repository;

import java.util.List;

import com.network.network_monitor.entity.NotificationLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByAlertIdOrderBySentAtDesc(Long alertId);

    List<NotificationLog> findByAlertIdAndChannel(Long alertId,
            com.network.network_monitor.enums.NotificationChannel channel);

    boolean existsByAlertIdAndChannelAndSuccess(Long alertId,
            com.network.network_monitor.enums.NotificationChannel channel, Boolean success);
}