package com.network.network_monitor.controller;

import com.network.network_monitor.dto.DeviceFilterDto;
import com.network.network_monitor.dto.DeviceGroupDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MonitoringUpdateDto;
import com.network.network_monitor.entity.NotificationLog;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.repository.NotificationLogRepository;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.DeviceService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OperationsWebController {

    private final DeviceService deviceService;
    private final DeviceGroupService deviceGroupService;
    private final NotificationLogRepository notificationLogRepository;

    @GetMapping("/inventory")
    public String inventory(@ModelAttribute("filter") DeviceFilterDto filter,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
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
        if (page > lastPage && devicePage.getTotalPages() > 0) {
            devicePage = deviceService.searchDevices(filter, PageRequest.of(lastPage, size, pageable.getSort()));
        }

        model.addAttribute("devicePage", devicePage);
        model.addAttribute("filter", filter != null ? filter : new DeviceFilterDto());
        model.addAttribute("deviceTypes", DeviceType.values());
        model.addAttribute("deviceStatuses", DeviceStatus.values());
        model.addAttribute("deviceGroups", deviceGroupService.getAllGroups());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);

        return "operations/inventory";
    }

    @GetMapping("/discovery")
    public String discovery(Model model) {
        model.addAttribute("devicePage", deviceService.getAllDevices(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
        return "operations/discovery";
    }

    @GetMapping("/monitoring")
    public String monitoring(@RequestParam(required = false) Long selectedDeviceId,
                             @RequestParam(required = false) Long selectedGroupId,
                             Model model) {
        List<DeviceGroupDto> groups = deviceGroupService.getAllGroups();
        Page<DeviceResponseDto> devices = deviceService.getAllDevices(
                PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "name", "id")));

        model.addAttribute("groups", groups);
        model.addAttribute("devicePage", devices);
        model.addAttribute("selectedDeviceId", selectedDeviceId);
        model.addAttribute("selectedGroupId", selectedGroupId);

        if (!model.containsAttribute("groupForm")) {
            model.addAttribute("groupForm", new DeviceGroupDto());
        }
        if (!model.containsAttribute("monitoringForm")) {
            model.addAttribute("monitoringForm", new MonitoringUpdateDto());
        }

        return "operations/monitoring";
    }

    @PostMapping("/monitoring/save")
    public String saveMonitoringConfig(@Valid @ModelAttribute("monitoringForm") MonitoringUpdateDto form,
                                       BindingResult result,
                                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/monitoring?selectedDeviceId=" + form.getDeviceId();
        }

        try {
            deviceService.updateMonitoringConfig(form);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật cấu hình giám sát thành công!");
        } catch (Exception e) {
            log.error("Lỗi cập nhật cấu hình giám sát: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể cập nhật: " + e.getMessage());
        }

        return "redirect:/monitoring?selectedDeviceId=" + form.getDeviceId();
    }

    @PostMapping("/monitoring/groups/save")
    public String saveGroup(@Valid @ModelAttribute("groupForm") DeviceGroupDto form,
                            BindingResult result,
                            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    result.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/monitoring";
        }

        try {
            deviceGroupService.saveGroup(form);
            redirectAttributes.addFlashAttribute("successMessage", "Lưu nhóm thiết bị thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi lưu nhóm thiết bị: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể lưu nhóm: " + e.getMessage());
        }

        return "redirect:/monitoring";
    }

    @PostMapping("/monitoring/groups/delete/{id}")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            deviceGroupService.deleteGroup(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa nhóm thiết bị thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi xóa nhóm thiết bị ID {}: ", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa nhóm: " + e.getMessage());
        }
        return "redirect:/monitoring";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        List<NotificationLog> logs = notificationLogRepository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "sentAt", "id"))).getContent();
        model.addAttribute("logs", logs);
        return "operations/notifications";
    }
}
