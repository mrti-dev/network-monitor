package com.network.network_monitor.controller;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.dto.AlertFilterDto;
import com.network.network_monitor.enums.AlertSeverity;
import com.network.network_monitor.enums.AlertStatus;
import com.network.network_monitor.service.AlertService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertWebController {

    private final AlertService alertService;

    @GetMapping
    public String listAlerts(@ModelAttribute("filter") AlertFilterDto filter,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             Model model) {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
        Page<AlertDto> alertPage = alertService.searchAlerts(filter, pageable);
        int lastPage = Math.max(0, alertPage.getTotalPages() - 1);
        if (page > lastPage) {
            alertPage = alertService.searchAlerts(filter, PageRequest.of(lastPage, size));
        }

        model.addAttribute("alertPage", alertPage);
        model.addAttribute("filter", filter != null ? filter : new AlertFilterDto());
        model.addAttribute("allStatuses", AlertStatus.values());
        model.addAttribute("allSeverities", AlertSeverity.values());

        return "alerts/list";
    }

    @PostMapping("/{id}/acknowledge")
    public String acknowledgeAlert(@PathVariable Long id,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        try {
            String username = principal != null ? principal.getName() : null;
            alertService.acknowledgeAlert(id, username);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận cảnh báo #" + id + " thành công.");
        } catch (Exception e) {
            log.error("Lỗi khi xác nhận cảnh báo ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xác nhận cảnh báo: " + e.getMessage());
        }
        return "redirect:/alerts";
    }

    @PostMapping("/{id}/resolve")
    public String resolveAlert(@PathVariable Long id,
                               RedirectAttributes redirectAttributes) {
        try {
            alertService.resolveAlert(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã giải quyết (đóng) cảnh báo #" + id + " thành công.");
        } catch (Exception e) {
            log.error("Lỗi khi giải quyết cảnh báo ID {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể giải quyết cảnh báo: " + e.getMessage());
        }
        return "redirect:/alerts";
    }
}
