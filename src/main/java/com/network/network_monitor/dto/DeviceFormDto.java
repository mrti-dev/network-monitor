package com.network.network_monitor.dto;

import com.network.network_monitor.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFormDto {

    private Long id;

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String name;

    @NotBlank(message = "IP không được để trống")
    @Pattern(regexp = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$", message = "Địa chỉ IP không hợp lệ")
    private String ipAddress;

    @Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$|^$", message = "Địa chỉ MAC không hợp lệ")
    private String macAddress;

    private DeviceType deviceType;

    private String location;
}
