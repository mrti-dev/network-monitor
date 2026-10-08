package com.network.network_monitor.dto;

import java.time.LocalDate;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.SlaPeriod;
import com.network.network_monitor.enums.SlaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaFilterDto {
    @Builder.Default
    private SlaPeriod period = SlaPeriod.LAST_7_DAYS;

    private LocalDate fromDate;
    private LocalDate toDate;

    private Long groupId;
    private DeviceType deviceType;
    private SlaStatus status;
}
