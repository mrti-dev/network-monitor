package com.network.network_monitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;
import com.network.network_monitor.enums.SlaPeriod;
import com.network.network_monitor.enums.SlaStatus;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.SlaReportService;

@ExtendWith(MockitoExtension.class)
class SlaReportWebControllerTest {

    @Mock
    private SlaReportService slaReportService;
    @Mock
    private DeviceGroupService deviceGroupService;

    @InjectMocks
    private SlaReportWebController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void viewSlaReport_returnsViewAndModel() throws Exception {
        SlaSummaryReportDto report = SlaSummaryReportDto.builder()
                .period(SlaPeriod.LAST_7_DAYS)
                .overallUptimePercentage(99.8)
                .deviceSlaList(Collections.emptyList())
                .build();

        when(slaReportService.generateReport(any())).thenReturn(report);
        when(deviceGroupService.getAllGroups()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/sla"))
                .andExpect(model().attributeExists("report"))
                .andExpect(model().attributeExists("groups"))
                .andExpect(model().attributeExists("filter"));
    }

    @Test
    void getSlaReportApi_returnsJson() throws Exception {
        SlaSummaryReportDto report = SlaSummaryReportDto.builder()
                .period(SlaPeriod.LAST_7_DAYS)
                .overallUptimePercentage(99.8)
                .totalDevicesCount(10)
                .metCount(9)
                .breachedCount(1)
                .build();

        when(slaReportService.generateReport(any())).thenReturn(report);

        mockMvc.perform(get("/api/reports/sla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallUptimePercentage").value(99.8))
                .andExpect(jsonPath("$.totalDevicesCount").value(10))
                .andExpect(jsonPath("$.metCount").value(9));
    }

    @Test
    void getDeviceSlaApi_returnsDeviceJson() throws Exception {
        DeviceSlaDto dto = DeviceSlaDto.builder()
                .deviceId(1L)
                .deviceName("Core-Router")
                .uptimePercentage(99.9)
                .slaStatus(SlaStatus.MET)
                .build();

        when(slaReportService.getDeviceSla(any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/reports/sla/device/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(1))
                .andExpect(jsonPath("$.deviceName").value("Core-Router"))
                .andExpect(jsonPath("$.slaStatus").value("MET"));
    }

    @Test
    void exportCsv_returnsCsvDownload() throws Exception {
        byte[] csv = "ID,Name\n1,Router\n".getBytes();
        when(slaReportService.exportCsv(any())).thenReturn(csv);

        mockMvc.perform(get("/reports/sla/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"sla_report_")))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().bytes(csv));
    }

    @Test
    void getSlaReportApi_invalidDate_returnsBadRequest() throws Exception {
        when(slaReportService.generateReport(any())).thenThrow(new IllegalArgumentException("Từ ngày không được lớn hơn đến ngày"));

        mockMvc.perform(get("/api/reports/sla"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Từ ngày không được lớn hơn đến ngày"));
    }
}
