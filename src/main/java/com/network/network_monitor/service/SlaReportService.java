package com.network.network_monitor.service;

import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;

public interface SlaReportService {
    SlaSummaryReportDto generateReport(SlaFilterDto filter);
    DeviceSlaDto getDeviceSla(Long deviceId, SlaFilterDto filter);
    byte[] exportCsv(SlaFilterDto filter);
}
