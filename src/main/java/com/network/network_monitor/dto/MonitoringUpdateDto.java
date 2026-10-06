package com.network.network_monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringUpdateDto {

    @NotNull(message = "ID thiết bị không được để trống")
    private Long deviceId;

    private Long groupId;

    @Min(value = 1, message = "Chu kỳ ping tối thiểu là 1 giây")
    @Max(value = 86400, message = "Chu kỳ ping tối đa là 86400 giây")
    private Integer pingInterval;

    @Min(value = 1, message = "Timeout tối thiểu là 1 ms")
    @Max(value = 60000, message = "Timeout tối đa là 60000 ms")
    private Integer timeoutMs;

    @Positive(message = "Ngưỡng độ trễ phải là số dương")
    @Max(value = 60000, message = "Ngưỡng độ trễ tối đa là 60000 ms")
    private Double latencyThreshold;

    private Boolean useGroupConfig;
    private Boolean isMonitored;
    private Boolean maintenanceMode;
}
