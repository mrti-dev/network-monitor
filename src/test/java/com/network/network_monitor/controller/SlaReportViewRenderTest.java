package com.network.network_monitor.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;
import com.network.network_monitor.enums.SlaPeriod;
import com.network.network_monitor.enums.SlaStatus;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.SlaReportService;

@WebMvcTest(SlaReportWebController.class)
@Import(SecurityConfig.class)
class SlaReportViewRenderTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SlaReportService slaReportService;

    @MockitoBean
    private DeviceGroupService deviceGroupService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Render Thymeleaf view SLA thành công khi có dữ liệu")
    void testRenderSlaView_SuccessWithData() throws Exception {
        DeviceSlaDto deviceSla = DeviceSlaDto.builder()
                .deviceId(1L)
                .deviceName("Core Router")
                .ipAddress("192.168.1.1")
                .uptimePercentage(99.8)
                .slaStatus(SlaStatus.MET)
                .targetUptimePercentage(99.5)
                .downtimeMinutes(20.0)
                .totalOutages(1)
                .build();

        SlaSummaryReportDto report = SlaSummaryReportDto.builder()
                .period(SlaPeriod.LAST_7_DAYS)
                .overallUptimePercentage(99.8)
                .totalDevicesCount(1)
                .metCount(1)
                .atRiskCount(0)
                .breachedCount(0)
                .deviceSlaList(List.of(deviceSla))
                .build();

        when(slaReportService.generateReport(any())).thenReturn(report);

        mvc.perform(get("/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/sla"))
                .andExpect(content().string(containsString("Core Router")))
                .andExpect(content().string(containsString("99.8%")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Render Thymeleaf view SLA khi cơ sở dữ liệu rỗng (0 thiết bị)")
    void testRenderSlaView_EmptyData() throws Exception {
        SlaSummaryReportDto emptyReport = SlaSummaryReportDto.builder()
                .period(SlaPeriod.LAST_7_DAYS)
                .overallUptimePercentage(null)
                .totalDevicesCount(0)
                .metCount(0)
                .atRiskCount(0)
                .breachedCount(0)
                .deviceSlaList(Collections.emptyList())
                .build();

        when(slaReportService.generateReport(any())).thenReturn(emptyReport);

        mvc.perform(get("/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/sla"))
                .andExpect(content().string(containsString("Không tìm thấy thiết bị nào")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Render SLA khi thiết bị chưa có metric (uptime null, trạng thái N/A)")
    void testRenderSlaView_MissingMetricsNotAvailable() throws Exception {
        DeviceSlaDto naDevice = DeviceSlaDto.builder()
                .deviceId(2L)
                .deviceName("Unknown Switch")
                .ipAddress("10.0.0.1")
                .uptimePercentage(null)
                .slaStatus(SlaStatus.NOT_AVAILABLE)
                .targetUptimePercentage(99.0)
                .build();

        SlaSummaryReportDto report = SlaSummaryReportDto.builder()
                .period(SlaPeriod.TODAY)
                .overallUptimePercentage(null)
                .totalDevicesCount(1)
                .metCount(0)
                .atRiskCount(0)
                .breachedCount(0)
                .deviceSlaList(List.of(naDevice))
                .build();

        when(slaReportService.generateReport(any())).thenReturn(report);

        mvc.perform(get("/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/sla"))
                .andExpect(content().string(containsString("Unknown Switch")))
                .andExpect(content().string(containsString("N/A")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Bắt lỗi khoảng ngày không hợp lệ, không redirect sang /error mà hiển thị errorMessage")
    void testRenderSlaView_InvalidDateRange_ShowsErrorMessage() throws Exception {
        when(slaReportService.generateReport(any(SlaFilterDto.class)))
                .thenThrow(new IllegalArgumentException("Từ ngày phải nhỏ hơn hoặc bằng đến ngày"))
                .thenReturn(SlaSummaryReportDto.builder()
                        .period(SlaPeriod.LAST_7_DAYS)
                        .totalDevicesCount(0)
                        .deviceSlaList(Collections.emptyList())
                        .build());

        mvc.perform(get("/reports/sla")
                        .param("period", "CUSTOM")
                        .param("fromDate", "2026-10-10")
                        .param("toDate", "2026-10-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/sla"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(containsString("Từ ngày phải nhỏ hơn hoặc bằng đến ngày")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("API GET /api/reports/sla trả JSON 200")
    void testGetSlaReportApi_Success() throws Exception {
        SlaSummaryReportDto report = SlaSummaryReportDto.builder()
                .period(SlaPeriod.LAST_30_DAYS)
                .overallUptimePercentage(99.9)
                .totalDevicesCount(1)
                .deviceSlaList(Collections.emptyList())
                .build();

        when(slaReportService.generateReport(any())).thenReturn(report);

        mvc.perform(get("/api/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("API GET /api/reports/sla trả 400 khi tham số không hợp lệ")
    void testGetSlaReportApi_BadRequest() throws Exception {
        when(slaReportService.generateReport(any()))
                .thenThrow(new IllegalArgumentException("Invalid date"));

        mvc.perform(get("/api/reports/sla"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid date")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Export CSV trả về file đính kèm với Content-Type text/csv")
    void testExportCsv_Success() throws Exception {
        byte[] csvContent = "Device ID,Device Name,IP Address,Group,Target Uptime (%),Uptime (%),Total Time,Up Time,Down Time,Status\n".getBytes();
        when(slaReportService.exportCsv(any())).thenReturn(csvContent);

        mvc.perform(get("/reports/sla/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=\"sla_report_")))
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().bytes(csvContent));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("Export CSV trả 400 khi khoảng thời gian không hợp lệ")
    void testExportCsv_InvalidPeriod() throws Exception {
        when(slaReportService.exportCsv(any()))
                .thenThrow(new IllegalArgumentException("Invalid date range"));

        mvc.perform(get("/reports/sla/export"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid date range")));
    }
}
