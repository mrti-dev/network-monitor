package com.network.network_monitor.enums;

/**
 * Loại sự cố được phát hiện bởi hệ thống giám sát.
 */
public enum AlertType {

    OFFLINE,
    HIGH_LATENCY,
    PACKET_LOSS,
    CPU_OVERLOAD,
    MEMORY_OVERLOAD,
    PORT_DOWN,
    RECOVERY,
    SNMP_UNREACHABLE,
    SNMP_CPU_HIGH,
    SNMP_MEMORY_HIGH,
    SNMP_INTERFACE_DOWN
}