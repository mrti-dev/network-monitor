package com.network.network_monitor.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

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
import com.network.network_monitor.dto.MaintenanceScheduleDto;
import com.network.network_monitor.dto.MaintenanceScheduleFormDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.MaintenanceScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/devices/{deviceId}/maintenance")
@RequiredArgsConstructor
public class MaintenanceWebController {

    private final MaintenanceScheduleService maintenanceService;
    private final DeviceService deviceService;

    @GetMapping
    public String viewDeviceMaintenance(@PathVariable Long deviceId, Model model) {
        DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
        List<MaintenanceScheduleDto> schedules = maintenanceService.getSchedulesByDeviceId(deviceId);

        MaintenanceScheduleFormDto form = MaintenanceScheduleFormDto.builder()
                .deviceId(deviceId)
                .startsAt(LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0))
                .endsAt(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
                .build();

        model.addAttribute("device", device);
        model.addAttribute("schedules", schedules);
        model.addAttribute("scheduleForm", form);

        return "devices/maintenance";
    }

    @PostMapping("/save")
    public String saveSchedule(@PathVariable Long deviceId,
                               @Valid @ModelAttribute("scheduleForm") MaintenanceScheduleFormDto form,
                               BindingResult result,
                               Principal principal,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        form.setDeviceId(deviceId);
        if (result.hasErrors()) {
            DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
            List<MaintenanceScheduleDto> schedules = maintenanceService.getSchedulesByDeviceId(deviceId);
            model.addAttribute("device", device);
            model.addAttribute("schedules", schedules);
            return "devices/maintenance";
        }

        try {
            String username = principal != null ? principal.getName() : null;
            if (form.getId() == null) {
                maintenanceService.createSchedule(form, username);
                redirectAttributes.addFlashAttribute("successMessage", "Tạo lịch bảo trì thành công!");
            } else {
                maintenanceService.updateSchedule(form.getId(), form, username);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật lịch bảo trì thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/devices/" + deviceId + "/maintenance";
    }

    @PostMapping("/{scheduleId}/cancel")
    public String cancelSchedule(@PathVariable Long deviceId,
                                 @PathVariable Long scheduleId,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        try {
            String username = principal != null ? principal.getName() : null;
            maintenanceService.cancelSchedule(scheduleId, username);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy lịch bảo trì thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/devices/" + deviceId + "/maintenance";
    }
}
