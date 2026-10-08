package com.network.network_monitor.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.SnmpConfigDto;
import com.network.network_monitor.dto.SnmpConfigFormDto;
import com.network.network_monitor.dto.SnmpInterfaceMetricDto;
import com.network.network_monitor.dto.SnmpMetricDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.SnmpService;
import com.network.network_monitor.strategy.SnmpProbeResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/devices/{deviceId}/snmp")
@RequiredArgsConstructor
public class SnmpWebController {

    private final SnmpService snmpService;
    private final DeviceService deviceService;

    @GetMapping
    public String viewSnmp(@PathVariable Long deviceId, Model model) {
        DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
        SnmpConfigDto config = snmpService.getConfigByDeviceId(deviceId);

        SnmpConfigFormDto form = new SnmpConfigFormDto();
        form.setDeviceId(deviceId);
        if (config != null) {
            form.setId(config.getId());
            form.setVersion(config.getVersion());
            form.setPort(config.getPort());
            form.setCommunity("");
            form.setTimeoutMs(config.getTimeoutMs());
            form.setRetries(config.getRetries());
            form.setEnabled(config.getEnabled());
        }

        SnmpMetricDto latestMetric = snmpService.getLatestMetric(deviceId);
        List<SnmpMetricDto> recentMetrics = snmpService.getRecentMetrics(deviceId, 20);
        List<SnmpInterfaceMetricDto> interfaces = snmpService.getLatestInterfaces(deviceId);

        model.addAttribute("device", device);
        model.addAttribute("snmpConfig", config);
        model.addAttribute("snmpForm", form);
        model.addAttribute("latestMetric", latestMetric);
        model.addAttribute("recentMetrics", recentMetrics);
        model.addAttribute("interfaces", interfaces);

        return "devices/snmp";
    }

    @PostMapping("/save")
    public String saveSnmp(@PathVariable Long deviceId,
                           @Valid @ModelAttribute("snmpForm") SnmpConfigFormDto form,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        form.setDeviceId(deviceId);
        if (result.hasErrors()) {
            DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
            SnmpConfigDto config = snmpService.getConfigByDeviceId(deviceId);
            model.addAttribute("device", device);
            model.addAttribute("snmpConfig", config);
            model.addAttribute("latestMetric", snmpService.getLatestMetric(deviceId));
            model.addAttribute("recentMetrics", snmpService.getRecentMetrics(deviceId, 20));
            model.addAttribute("interfaces", snmpService.getLatestInterfaces(deviceId));
            return "devices/snmp";
        }

        try {
            snmpService.saveConfig(deviceId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Cấu hình SNMP đã được lưu thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/devices/" + deviceId + "/snmp";
    }

    @PostMapping("/toggle")
    public String toggleSnmp(@PathVariable Long deviceId, RedirectAttributes redirectAttributes) {
        try {
            snmpService.toggleSnmp(deviceId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái SNMP!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/devices/" + deviceId + "/snmp";
    }

    @PostMapping("/delete")
    public String deleteSnmp(@PathVariable Long deviceId, RedirectAttributes redirectAttributes) {
        try {
            snmpService.deleteConfig(deviceId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa cấu hình SNMP!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/devices/" + deviceId + "/snmp";
    }

    @PostMapping("/test")
    public String testConnection(@PathVariable Long deviceId, RedirectAttributes redirectAttributes) {
        try {
            SnmpProbeResult probe = snmpService.testConnection(deviceId);
            if (probe.isReachable()) {
                String info = "Kết nối SNMP thành công! Thiết bị: " + (probe.getSysName() != null ? probe.getSysName() : "N/A");
                if (probe.getCpuUsage() != null) {
                    info += String.format(" | CPU: %.1f%%", probe.getCpuUsage());
                }
                if (probe.getMemoryUsage() != null) {
                    info += String.format(" | RAM: %.1f%%", probe.getMemoryUsage());
                }
                redirectAttributes.addFlashAttribute("successMessage", info);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Kết nối SNMP thất bại: " + probe.getFailureReason());
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi kiểm tra SNMP: " + e.getMessage());
        }
        return "redirect:/devices/" + deviceId + "/snmp";
    }

    @GetMapping("/metrics/json")
    @ResponseBody
    public java.util.Map<String, Object> getMetricsJson(@PathVariable Long deviceId) {
        SnmpMetricDto latest = snmpService.getLatestMetric(deviceId);
        List<SnmpMetricDto> recent = snmpService.getRecentMetrics(deviceId, 20);
        List<SnmpInterfaceMetricDto> interfaces = snmpService.getLatestInterfaces(deviceId);
        return java.util.Map.of(
                "latest", latest != null ? latest : java.util.Map.of(),
                "recent", recent,
                "interfaces", interfaces
        );
    }
}
