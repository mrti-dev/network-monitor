package com.network.network_monitor.dto;

import java.time.LocalDateTime;
import com.network.network_monitor.enums.SnmpVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpConfigDto {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String ipAddress;
    private SnmpVersion version;
    private Integer port;
    private String community; // Masked: "******"
    private Integer timeoutMs;
    private Integer retries;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
