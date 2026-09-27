package com.network.network_monitor.controller;

import com.network.network_monitor.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class OperationsWebController {

    private final DeviceService deviceService;

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("devicePage", deviceService.getAllDevices(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
        return "operations/inventory";
    }

    @GetMapping("/discovery")
    public String discovery(Model model) {
        model.addAttribute("devicePage", deviceService.getAllDevices(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
        return "operations/discovery";
    }

    @GetMapping("/monitoring")
    public String monitoring(Model model) {
        model.addAttribute("devicePage", deviceService.getAllDevices(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "name"))));
        return "operations/monitoring";
    }

    @GetMapping("/notifications")
    public String notifications() {
        return "operations/notifications";
    }
}
