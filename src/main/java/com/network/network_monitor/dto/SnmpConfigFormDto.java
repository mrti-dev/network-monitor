package com.network.network_monitor.dto;

import com.network.network_monitor.enums.SnmpVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnmpConfigFormDto {
    private Long id;
    private Long deviceId;

    @NotNull(message = "Phiên bản SNMP không được để trống")
    @Builder.Default
    private SnmpVersion version = SnmpVersion.V2C;

    @NotNull(message = "Cổng SNMP không được để trống")
    @Min(value = 1, message = "Cổng phải từ 1 đến 65535")
    @Max(value = 65535, message = "Cổng phải từ 1 đến 65535")
    @Builder.Default
    private Integer port = 161;

    // Community string: nếu để trống khi update thì giữ nguyên giá trị cũ
    private String community;

    @Min(value = 500, message = "Timeout tối thiểu 500ms")
    @Max(value = 30000, message = "Timeout tối đa 30000ms")
    @Builder.Default
    private Integer timeoutMs = 3000;

    @Min(value = 0, message = "Số lần thử lại không được âm")
    @Max(value = 5, message = "Số lần thử lại tối đa 5")
    @Builder.Default
    private Integer retries = 2;

    @Builder.Default
    private Boolean enabled = true;
}
