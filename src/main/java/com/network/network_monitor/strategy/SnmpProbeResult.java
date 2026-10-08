package com.network.network_monitor.strategy;

import com.network.network_monitor.enums.SnmpInterfaceStatus;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Kết quả probe SNMP từ một thiết bị.
 * Các trường null = OID không được thiết bị hỗ trợ.
 */
@Getter
@Builder
public class SnmpProbeResult {

    private final boolean reachable;

    private final String sysName;
    private final Long sysUptime;

    /** null = OID không hỗ trợ */
    private final Double cpuUsage;
    private final Double memoryUsage;

    /** Bandwidth inbound tổng hợp (bps), null nếu chưa đủ 2 mẫu để tính delta */
    private final Double inboundBps;
    private final Double outboundBps;

    private final List<InterfaceInfo> interfaces;

    /** Lý do thất bại, null nếu thành công */
    private final String failureReason;

    @Getter
    @Builder
    public static class InterfaceInfo {
        private final int index;
        private final String name;
        private final SnmpInterfaceStatus status;
        private final Long inboundOctets;
        private final Long outboundOctets;
    }
}
