package com.network.network_monitor.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ScanRequestDto {
    @Pattern(regexp = "^$|^([0-9]{1,3}\\.){3}[0-9]{1,3}/([0-9]|[1-2][0-9]|3[0-2])$", message = "Sai định dạng CIDR (VD: 192.168.1.0/24)")
    private String subnet;
}
