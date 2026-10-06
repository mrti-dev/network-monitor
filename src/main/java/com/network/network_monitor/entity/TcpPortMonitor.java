package com.network.network_monitor.entity;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.TcpPortStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tcp_port_monitors",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_device_port", columnNames = {"device_id", "port"})
        },
        indexes = {
                @Index(name = "idx_tcp_port_device", columnList = "device_id"),
                @Index(name = "idx_tcp_port_status", columnList = "status"),
                @Index(name = "idx_tcp_port_enabled", columnList = "enabled")
        })
public class TcpPortMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 3000;

    @Builder.Default
    @Column(name = "failure_threshold", nullable = false)
    private Integer failureThreshold = 3;

    @Builder.Default
    @Column(name = "success_threshold", nullable = false)
    private Integer successThreshold = 2;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private TcpPortStatus status = TcpPortStatus.UNKNOWN;

    @Builder.Default
    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Builder.Default
    @Column(name = "consecutive_successes", nullable = false)
    private Integer consecutiveSuccesses = 0;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_status_changed_at")
    private LocalDateTime lastStatusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.enabled == null) {
            this.enabled = true;
        }
        if (this.status == null) {
            this.status = TcpPortStatus.UNKNOWN;
        }
        if (this.consecutiveFailures == null) {
            this.consecutiveFailures = 0;
        }
        if (this.consecutiveSuccesses == null) {
            this.consecutiveSuccesses = 0;
        }
        if (this.timeoutMs == null) {
            this.timeoutMs = 3000;
        }
        if (this.failureThreshold == null) {
            this.failureThreshold = 3;
        }
        if (this.successThreshold == null) {
            this.successThreshold = 2;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
