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
import com.network.network_monitor.dto.TcpPortDto;
import com.network.network_monitor.dto.TcpPortFormDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.TcpPortMonitorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/devices/{deviceId}/ports")
@RequiredArgsConstructor
public class TcpPortWebController {

    private final TcpPortMonitorService portService;
    private final DeviceService deviceService;

    @GetMapping
    public String viewDevicePorts(@PathVariable Long deviceId, Model model) {
        DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
        List<TcpPortDto> ports = portService.getPortsByDeviceId(deviceId);

        TcpPortFormDto form = new TcpPortFormDto();
        form.setDeviceId(deviceId);

        model.addAttribute("device", device);
        model.addAttribute("ports", ports);
        model.addAttribute("portForm", form);

        return "devices/ports";
    }

    @GetMapping("/json")
    @ResponseBody
    public List<TcpPortDto> getDevicePortsJson(@PathVariable Long deviceId) {
        return portService.getPortsByDeviceId(deviceId);
    }

    @PostMapping("/save")
    public String savePort(@PathVariable Long deviceId,
                           @Valid @ModelAttribute("portForm") TcpPortFormDto form,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        form.setDeviceId(deviceId);
        if (result.hasErrors()) {
            DeviceFormDto device = deviceService.getDeviceFormById(deviceId);
            List<TcpPortDto> ports = portService.getPortsByDeviceId(deviceId);
            model.addAttribute("device", device);
            model.addAttribute("ports", ports);
            return "devices/ports";
        }

        try {
            if (form.getId() == null) {
                portService.createPort(deviceId, form);
                redirectAttributes.addFlashAttribute("successMessage", "Thêm cấu hình port thành công!");
            } else {
                portService.updatePort(form.getId(), form);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật cấu hình port thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/devices/" + deviceId + "/ports";
    }

    @PostMapping("/{portId}/toggle")
    public String togglePort(@PathVariable Long deviceId,
                             @PathVariable Long portId,
                             RedirectAttributes redirectAttributes) {
        try {
            portService.togglePort(portId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi trạng thái giám sát port!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/devices/" + deviceId + "/ports";
    }

    @PostMapping("/{portId}/delete")
    public String deletePort(@PathVariable Long deviceId,
                             @PathVariable Long portId,
                             RedirectAttributes redirectAttributes) {
        try {
            portService.deletePort(portId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa cấu hình port!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/devices/" + deviceId + "/ports";
    }
}
