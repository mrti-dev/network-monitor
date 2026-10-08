package com.network.network_monitor.entity;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.ServiceType;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service_availability_logs", indexes = {
        @Index(name = "idx_availability_device_period", columnList = "device_id,service_type,recorded_at"),
        @Index(name = "idx_availability_monitor_period", columnList = "monitor_id,recorded_at")
})
public class ServiceAvailabilityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(name = "monitor_id")
    private Long monitorId;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
