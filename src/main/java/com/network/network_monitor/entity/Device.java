package com.network.network_monitor.entity;

import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.jdbc.Expectation;

/**
 * JPA Entity map với bảng {@code devices} — thiết bị mạng được quản lý.
 * Áp dụng Soft Delete: mọi truy vấn mặc định loại trừ bản ghi đã xóa.
 * Sử dụng cờ {@code isMonitored} để bật/tắt giám sát thay vì xóa khi thiết bị ngắt kết nối.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "devices", indexes = {
        @Index(name = "idx_device_status", columnList = "status"),
        @Index(name = "idx_device_type", columnList = "device_type"),
        @Index(name = "idx_device_ip", columnList = "ip_address"),
        @Index(name = "idx_device_monitored", columnList = "is_monitored")
})
@SQLDelete(sql = "UPDATE devices SET is_deleted = true, version = version + 1 WHERE id = ? AND version = ?", verify = Expectation.RowCount.class)
@SQLRestriction("is_deleted = false")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "ip_address", nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 30)
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeviceStatus status;

    @Column(name = "location", length = 255)
    private String location;

    @Builder.Default
    @Column(name = "is_monitored", nullable = false)
    private Boolean isMonitored = true;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    @OneToOne(mappedBy = "device", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private MonitoringConfig monitoringConfig;

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    @Builder.Default
    private List<MetricLog> metricLogs = new ArrayList<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();

    @OneToMany(mappedBy = "device", fetch = FetchType.LAZY)
    @Builder.Default
    private List<DeviceLog> deviceLogs = new ArrayList<>();

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
