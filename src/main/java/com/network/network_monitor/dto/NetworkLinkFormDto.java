package com.network.network_monitor.dto;

import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkLinkFormDto {
    private Long id;

    @NotNull(message = "Thiết bị nguồn không được để trống")
    private Long sourceDeviceId;

    @NotNull(message = "Thiết bị đích không được để trống")
    private Long targetDeviceId;

    @Builder.Default
    private LinkType linkType = LinkType.ETHERNET;

    private String sourcePort;
    private String targetPort;
    private Integer bandwidthMbps;

    @Builder.Default
    private LinkStatus status = LinkStatus.UP;

    private String description;
}
