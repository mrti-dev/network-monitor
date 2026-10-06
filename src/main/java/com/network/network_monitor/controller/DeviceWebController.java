package com.network.network_monitor.controller;

import com.network.network_monitor.dto.DashboardSummaryDto;
import com.network.network_monitor.dto.DeviceFilterDto;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.service.DashboardService;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.DeviceService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@Slf4j
@Controller
@RequestMapping({"/", "/devices"})
@RequiredArgsConstructor
public class DeviceWebController {

    private final DeviceService deviceService;
    private final DeviceGroupService deviceGroupService;
    private final DashboardService dashboardService;

    @GetMapping
    public String listDevices(@ModelAttribute("filter") DeviceFilterDto filter,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "15") int size,
                              @RequestParam(defaultValue = "createdAt") String sortBy,
                              @RequestParam(defaultValue = "desc") String direction,
                              Model model) {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy, "id"));

        Page<DeviceResponseDto> devicePage = deviceService.searchDevices(filter, pageable);
        int lastPage = Math.max(0, devicePage.getTotalPages() - 1);
        if (page > lastPage) {
            devicePage = deviceService.searchDevices(filter, PageRequest.of(lastPage, size, pageable.getSort()));
        }

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        model.addAttribute("devicePage", devicePage);
        model.addAttribute("dashboard", summary);
        model.addAttribute("filter", filter != null ? filter : new DeviceFilterDto());
        model.addAttribute("deviceTypes", DeviceType.values());
        model.addAttribute("deviceStatuses", DeviceStatus.values());
        model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);

        return "devices/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("deviceForm", new DeviceFormDto());
        model.addAttribute("deviceTypes", DeviceType.values());
        model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
        return "devices/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("deviceForm", deviceService.getDeviceFormById(id));
        model.addAttribute("deviceTypes", DeviceType.values());
        model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
        return "devices/form";
    }

    @PostMapping("/save")
    public String saveDevice(@Valid @ModelAttribute("deviceForm") DeviceFormDto form,
                             BindingResult result,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("deviceTypes", DeviceType.values());
            model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
            return "devices/form";
        }

        try {
            deviceService.saveDevice(form);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu thông tin thiết bị thành công!");
        } catch (DuplicateResourceException e) {
            model.addAttribute("deviceTypes", DeviceType.values());
            model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
            model.addAttribute("errorMessage", e.getMessage());
            return "devices/form";
        } catch (Exception e) {
            log.error("Lỗi khi lưu thiết bị: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể lưu thiết bị. Vui lòng thử lại sau.");
            return "redirect:/devices";
        }

        return "redirect:/devices";
    }

    @PostMapping("/delete/{id}")
    public String deleteDevice(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceService.deleteDevice(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa thiết bị thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi xóa thiết bị ID {}: ", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa thiết bị: " + e.getMessage());
        }
        return "redirect:/devices";
    }

    @PostMapping("/toggle/{id}")
    public String toggleMonitoring(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceService.toggleMonitoring(id);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái giám sát thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi chuyển đổi giám sát thiết bị ID {}: ", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật giám sát: " + e.getMessage());
        }
        return "redirect:/devices";
    }

    @PostMapping("/maintenance/{id}")
    public String toggleMaintenance(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceService.toggleMaintenanceMode(id);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật chế độ bảo trì thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi chuyển đổi bảo trì thiết bị ID {}: ", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật bảo trì: " + e.getMessage());
        }
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
