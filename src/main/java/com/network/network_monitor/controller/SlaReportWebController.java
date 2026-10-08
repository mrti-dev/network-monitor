package com.network.network_monitor.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import com.network.network_monitor.dto.DeviceGroupDto;
import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.SlaReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SlaReportWebController {

    private final SlaReportService slaReportService;
    private final DeviceGroupService deviceGroupService;

    @GetMapping("/reports/sla")
    public String viewSlaReport(@ModelAttribute("filter") SlaFilterDto filter, Model model) {
        SlaSummaryReportDto report;
        try {
            report = slaReportService.generateReport(filter);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            filter = SlaFilterDto.builder().period(com.network.network_monitor.enums.SlaPeriod.LAST_7_DAYS).build();
            report = slaReportService.generateReport(filter);
        }
        List<DeviceGroupDto> groups = deviceGroupService.getAllGroups();

        model.addAttribute("report", report);
        model.addAttribute("groups", groups);
        model.addAttribute("filter", filter);

        return "reports/sla";
    }

    @GetMapping("/api/reports/sla")
    @ResponseBody
    public ResponseEntity<?> getSlaReportApi(SlaFilterDto filter) {
        try {
            return ResponseEntity.ok(slaReportService.generateReport(filter));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/reports/sla/device/{deviceId}")
    @ResponseBody
    public ResponseEntity<?> getDeviceSlaApi(@PathVariable Long deviceId, SlaFilterDto filter) {
        try {
            return ResponseEntity.ok(slaReportService.getDeviceSla(deviceId, filter));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/reports/sla/export")
    public ResponseEntity<?> exportCsv(SlaFilterDto filter) {
        try {
            byte[] csvData = slaReportService.exportCsv(filter);
            String filename = "sla_report_" + java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csvData);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        }
    }
}
