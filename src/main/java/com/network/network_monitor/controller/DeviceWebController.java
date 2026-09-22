package com.network.network_monitor.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.service.DeviceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping({"/", "/devices"})
@RequiredArgsConstructor
public class DeviceWebController {

    private final DeviceService deviceService;
    private final com.network.network_monitor.repository.DeviceRepository deviceRepository;
    private final com.network.network_monitor.repository.MetricLogRepository metricLogRepository;

    @GetMapping
    public String listDevices(Model model) {
        model.addAttribute("devices", deviceService.getAllDevices());
        return "devices/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("deviceForm", new DeviceFormDto());
        model.addAttribute("deviceTypes", DeviceType.values());
        return "devices/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("deviceForm", deviceService.getDeviceFormById(id));
        model.addAttribute("deviceTypes", DeviceType.values());
        return "devices/form";
    }

    @PostMapping("/save")
    public String saveDevice(@Valid @ModelAttribute("deviceForm") DeviceFormDto form,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("deviceTypes", DeviceType.values());
            return "devices/form";
        }
        
        try {
            deviceService.saveDevice(form);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu thông tin thiết bị thành công!");
        } catch (DuplicateResourceException e) {
            model.addAttribute("deviceTypes", DeviceType.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "devices/form";
        } catch (Exception e) {
            model.addAttribute("deviceTypes", DeviceType.values());
            model.addAttribute("errorMessage", "Lỗi hệ thống: " + e.getMessage());
            return "devices/form";
        }
        
        return "redirect:/devices";
    }

    @GetMapping("/delete/{id}")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        deviceService.deleteDevice(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thiết bị thành công!");
        return "redirect:/devices";
    }

    @GetMapping("/toggle/{id}")
    public String toggleMonitoring(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        deviceService.toggleMonitoring(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái giám sát thành công!");
        return "redirect:/devices";
    }

    @GetMapping("/metrics/{id}")
    public String viewMetrics(@PathVariable Long id, Model model) {
        com.network.network_monitor.entity.Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Invalid device Id:" + id));
        
        java.util.List<com.network.network_monitor.entity.MetricLog> rawMetrics = metricLogRepository.findByDeviceIdOrderByRecordedAtDesc(id);
        java.util.List<com.network.network_monitor.entity.MetricLog> metrics = new java.util.ArrayList<>(rawMetrics);
        
        // Limit to latest 30 metrics for chart to not overload UI
        if (metrics.size() > 30) {
            metrics = metrics.subList(0, 30);
        }
        
        java.util.Collections.reverse(metrics); // chronological order for chart

        model.addAttribute("device", device);
        model.addAttribute("metrics", metrics);
        return "devices/metrics";
    }
}
