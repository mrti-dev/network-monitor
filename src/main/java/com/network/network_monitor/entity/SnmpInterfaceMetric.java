package com.network.network_monitor.entity;

import com.network.network_monitor.enums.SnmpInterfaceStatus;
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
@Table(name = "snmp_interface_metrics", indexes = {
        @Index(name = "idx_snmp_iface_device_time", columnList = "device_id, recorded_at"),
        @Index(name = "idx_snmp_iface_device_index", columnList = "device_id, interface_index")
})
public class SnmpInterfaceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    @Column(name = "interface_name", length = 255)
    private String interfaceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    @Builder.Default
    private SnmpInterfaceStatus operationalStatus = SnmpInterfaceStatus.UNKNOWN;

    @Column(name = "inbound_octets")
    private Long inboundOctets;

    @Column(name = "outbound_octets")
    private Long outboundOctets;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
