package com.network.network_monitor.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity map với bảng {@code metric_logs} — bản ghi số liệu đo đạc
 * (time-series) của thiết bị. Không áp dụng soft delete (audit/time-series).
 * Dọn dẹp qua cron job tự động cho dữ liệu > 7 ngày.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "metric_logs", indexes = {
        @Index(name = "idx_device_time", columnList = "device_id, recorded_at"),
        @Index(name = "idx_metric_recorded_at", columnList = "recorded_at")
})
public class MetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "latency_ms")
    private Double latencyMs;

    @Column(name = "packet_loss")
    private Double packetLoss;

    @Column(name = "is_reachable", nullable = false)
    private Boolean isReachable;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }
}