package com.network.network_monitor.entity;

import com.network.network_monitor.enums.ProbingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity map với bảng {@code monitoring_configs} — cấu hình giám sát
 * riêng cho từng thiết bị (chu kỳ quét, ngưỡng cảnh báo, chiến lược thăm dò).
 * Quan hệ 1-1 với {@link Device} dùng shared primary key (device_id).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "monitoring_configs")
public class MonitoringConfig {

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "ping_interval", nullable = false)
    private Integer pingInterval;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;

    @Column(name = "latency_threshold", nullable = false)
    private Double latencyThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 20)
    private ProbingMethod strategyType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
