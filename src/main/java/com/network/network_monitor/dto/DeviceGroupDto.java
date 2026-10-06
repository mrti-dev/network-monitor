package com.network.network_monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceGroupDto {
    private Long id;

    @NotBlank(message = "Tên nhóm không được để trống")
    @Size(max = 100, message = "Tên nhóm không vượt quá 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả không vượt quá 255 ký tự")
    private String description;

    @Positive(message = "Chu kỳ ping phải lớn hơn 0")
    @Max(value = 86400, message = "Chu kỳ ping tối đa 86400 giây")
    private Integer pingInterval;

    @Positive(message = "Timeout phải lớn hơn 0")
    @Max(value = 60000, message = "Timeout tối đa 60000 ms")
    private Integer timeoutMs;

    @Positive(message = "Ngưỡng độ trễ phải lớn hơn 0")
    @Max(value = 60000, message = "Ngưỡng độ trễ tối đa 60000 ms")
    private Double latencyThreshold;

    private int deviceCount;
}
