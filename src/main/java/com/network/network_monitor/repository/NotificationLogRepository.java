package com.network.network_monitor.repository;

import java.util.List;

import com.network.network_monitor.entity.NotificationLog;
import com.network.network_monitor.enums.NotificationChannel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByAlertIdOrderBySentAtDesc(Long alertId);

    List<NotificationLog> findByAlertIdAndChannel(Long alertId, NotificationChannel channel);

    boolean existsByAlertIdAndChannelAndStatus(Long alertId, NotificationChannel channel, String status);
}