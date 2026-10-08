package com.network.network_monitor.entity;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.LinkDiscoverySource;
import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;

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
@Table(name = "network_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_link_device_interfaces", columnNames = {
                        "source_device_id", "source_port", "target_device_id", "target_port"})
        },
        indexes = {
                @Index(name = "idx_link_source", columnList = "source_device_id"),
                @Index(name = "idx_link_target", columnList = "target_device_id"),
                @Index(name = "idx_link_status", columnList = "status"),
                @Index(name = "idx_link_discovery_source", columnList = "discovery_source")
        })
public class NetworkLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_device_id", nullable = false)
    private Device sourceDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_device_id", nullable = false)
    private Device targetDevice;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    @Builder.Default
    private LinkType linkType = LinkType.ETHERNET;

    @Column(name = "source_port", length = 50)
    private String sourcePort;

    @Column(name = "target_port", length = 50)
    private String targetPort;

    @Column(name = "bandwidth_mbps")
    private Integer bandwidthMbps;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LinkStatus status = LinkStatus.UP;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_source", nullable = false, length = 20)
    @Builder.Default
    private LinkDiscoverySource discoverySource = LinkDiscoverySource.MANUAL;

    @Column(name = "last_discovered_at")
    private LocalDateTime lastDiscoveredAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        validateNoSelfLink();
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        validateNoSelfLink();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateNoSelfLink() {
        if (sourceDevice != null && targetDevice != null &&
                java.util.Objects.equals(sourceDevice.getId(), targetDevice.getId())) {
            throw new IllegalArgumentException("Thiết bị nguồn và thiết bị đích không được trùng nhau");
        }
    }
}
