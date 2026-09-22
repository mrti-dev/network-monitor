package com.network.network_monitor.dto;

import java.time.LocalDateTime;

import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponseDto {
    private Long id;
    private String name;
    private String ipAddress;
    private String macAddress;
    private DeviceType deviceType;
    private DeviceStatus status;
    private String location;
    private Boolean isMonitored;
    private LocalDateTime updatedAt;
}
