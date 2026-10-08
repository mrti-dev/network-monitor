package com.network.network_monitor.enums;

public enum SlaStatus {
    NOT_AVAILABLE,
    MET,       // Đạt chỉ tiêu SLA (>= target)
    AT_RISK,   // Có nguy cơ vi phạm (cận ngưỡng, e.g. target - 0.5% đến target)
    BREACHED   // Vi phạm SLA (< target)
}
