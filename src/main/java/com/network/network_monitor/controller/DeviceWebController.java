package com.network.network_monitor.controller;

import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.service.DeviceService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping({"/", "/devices"})
@RequiredArgsConstructor
public class DeviceWebController {

    private final DeviceService deviceService;

    @GetMapping
    public String listDevices(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "15") int size,
                              Model model) {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var devicePage = deviceService.getAllDevices(pageable);
        int lastPage = Math.max(0, devicePage.getTotalPages() - 1);
        if (page > lastPage) devicePage = deviceService.getAllDevices(PageRequest.of(lastPage, size, pageable.getSort()));
        model.addAttribute("devicePage", devicePage);
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
        }

        return "redirect:/devices";
    }

    @PostMapping("/delete/{id}")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        deviceService.deleteDevice(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thiết bị thành công!");
        return "redirect:/devices";
    }

    @PostMapping("/toggle/{id}")
    public String toggleMonitoring(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        deviceService.toggleMonitoring(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái giám sát thành công!");
        return "redirect:/devices";
    }

    @GetMapping("/metrics/{id}")
    public String viewMetrics(@PathVariable Long id, Model model) {
        DeviceFormDto device = deviceService.getDeviceFormById(id);
        List<MetricLogDto> metrics = deviceService.getDeviceMetrics(id);
        Double threshold = deviceService.getLatencyThreshold(id);

        List<MetricLogDto> mutableMetrics = new ArrayList<>(metrics);
        Collections.reverse(mutableMetrics); // Thứ tự thời gian tăng dần cho biểu đồ

        model.addAttribute("device", device);
        model.addAttribute("metrics", mutableMetrics);
        model.addAttribute("latencyThreshold", threshold);
        return "devices/metrics";
    }
}
