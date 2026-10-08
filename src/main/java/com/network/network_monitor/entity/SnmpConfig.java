package com.network.network_monitor.entity;

import com.network.network_monitor.enums.SnmpVersion;
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
 * Cấu hình SNMP cho từng thiết bị. Community string luôn được mã hóa trước khi lưu.
 * Không trả communityEncrypted trong DTO hoặc API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "snmp_configs", indexes = {
        @Index(name = "idx_snmp_config_device", columnList = "device_id"),
        @Index(name = "idx_snmp_config_enabled", columnList = "enabled")
})
public class SnmpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "version", nullable = false, length = 10)
    @Builder.Default
    private SnmpVersion version = SnmpVersion.V2C;

    @Column(name = "port", nullable = false)
    @Builder.Default
    private Integer port = 161;

    @Column(name = "community_encrypted", nullable = false, length = 512)
    private String communityEncrypted;

    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeoutMs = 3000;

    @Column(name = "retries", nullable = false)
    @Builder.Default
    private Integer retries = 2;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
