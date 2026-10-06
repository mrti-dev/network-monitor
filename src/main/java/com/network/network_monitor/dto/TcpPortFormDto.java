package com.network.network_monitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TcpPortFormDto {

    private Long id;

    @NotNull(message = "ID thiết bị không được để trống")
    private Long deviceId;

    @NotNull(message = "Cổng TCP không được để trống")
    @Min(value = 1, message = "Cổng phải từ 1 đến 65535")
    @Max(value = 65535, message = "Cổng phải từ 1 đến 65535")
    private Integer port;

    @Size(max = 100, message = "Tên dịch vụ tối đa 100 ký tự")
    private String serviceName;

    @Builder.Default
    private Boolean enabled = true;

    @NotNull(message = "Timeout không được để trống")
    @Min(value = 100, message = "Timeout tối thiểu 100ms")
    @Max(value = 30000, message = "Timeout tối đa 30000ms")
    @Builder.Default
    private Integer timeoutMs = 3000;

    @NotNull(message = "Ngưỡng lỗi không được để trống")
    @Min(value = 1, message = "Ngưỡng lỗi phải lớn hơn 0")
    @Max(value = 100, message = "Ngưỡng lỗi tối đa 100")
    @Builder.Default
    private Integer failureThreshold = 3;

    @NotNull(message = "Ngưỡng phục hồi không được để trống")
    @Min(value = 1, message = "Ngưỡng phục hồi phải lớn hơn 0")
    @Max(value = 100, message = "Ngưỡng phục hồi tối đa 100")
    @Builder.Default
    private Integer successThreshold = 2;
}
