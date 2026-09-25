package com.network.network_monitor.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Giới hạn tài nguyên cho discovery scan.
 * Đọc từ prefix {@code app.discovery.*} trong application.properties
 * hoặc biến môi trường {@code APP_DISCOVERY_*}.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.discovery")
public class DiscoveryLimits {

    /**
     * Số địa chỉ host tối đa được phép quét trong một lần scan.
     * Giá trị mặc định: 254 (tương đương /24).
     */
    @Positive @Max(65534)
    private int maxHostsPerScan = 254;

    /**
     * Số lượt scan đồng thời tối đa trên toàn ứng dụng.
     */
    @Positive @Max(10)
    private int maxConcurrentScans = 3;

    /**
     * Số task probe (ping + ARP) chạy song song bên trong một lượt scan.
     */
    @Positive @Max(512)
    private int probeConcurrency = 32;

    /**
     * Tổng timeout (giây) cho toàn bộ một lượt scan.
     * Khi vượt quá, trả kết quả một phần với trạng thái PARTIAL.
     */
    @Positive @Max(300)
    private int scanTimeoutSeconds = 60;

    /**
     * Timeout (ms) cho mỗi lệnh ping/ICMP probe.
     */
    @Positive @Max(30000)
    private int probeTimeoutMs = 2000;

    /**
     * Timeout (ms) cho lệnh ARP lookup.
     */
    @Positive @Max(10000)
    private int arpTimeoutMs = 1500;
}
