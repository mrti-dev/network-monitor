package com.network.network_monitor.dto;

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
public class DeviceFilterDto {
    private String keyword;
    private DeviceStatus status;
    private DeviceType deviceType;
    private String location;
    private Boolean monitored;
    private Long groupId;
}
