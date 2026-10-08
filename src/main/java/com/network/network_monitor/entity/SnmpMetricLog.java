package com.network.network_monitor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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
@Table(name = "snmp_metric_logs", indexes = {
        @Index(name = "idx_snmp_metric_device_time", columnList = "device_id, recorded_at"),
        @Index(name = "idx_snmp_metric_recorded_at", columnList = "recorded_at")
})
public class SnmpMetricLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "sys_uptime")
    private Long sysUptime;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "inbound_bps")
    private Double inboundBps;

    @Column(name = "outbound_bps")
    private Double outboundBps;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
