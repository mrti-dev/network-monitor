package com.network.network_monitor.entity;

import java.math.BigDecimal;

import com.network.network_monitor.enums.ProbingMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA Entity map với bảng {@code monitoring_configs} — cấu hình giám sát
 * riêng cho từng thiết bị (chu kỳ quét, ngưỡng cảnh báo, SNMP).
 * Quan hệ 1-1 với {@link Device}; khóa ngoại {@code device_id} nằm ở bảng này.
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "device_id", unique = true, nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "probing_method", nullable = false, length = 20)
    private ProbingMethod probingMethod;

    @Column(name = "custom_interval_ms")
    private Long customIntervalMs;

    @Column(name = "tcp_ports", length = 255)
    private String tcpPorts;

    @Column(name = "latency_warning_ms")
    private Integer latencyWarningMs;

    @Column(name = "latency_critical_ms")
    private Integer latencyCriticalMs;

    @Column(name = "packet_loss_warning_pct", precision = 5, scale = 2)
    private BigDecimal packetLossWarningPct;

    @Column(name = "packet_loss_critical_pct", precision = 5, scale = 2)
    private BigDecimal packetLossCriticalPct;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures;

    @Column(name = "consecutive_success")
    private Integer consecutiveSuccess;

    @Column(name = "snmp_enabled")
    private Boolean snmpEnabled;

    @Column(name = "snmp_community", length = 100)
    private String snmpCommunity;

    @Column(name = "snmp_version", length = 10)
    private String snmpVersion;

    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }
}