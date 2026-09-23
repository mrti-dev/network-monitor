package com.network.network_monitor.controller;

import com.network.network_monitor.dto.AlertDto;
import com.network.network_monitor.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/alerts")
public class AlertWebController {

    private final AlertService alertService;

    @Autowired
    public AlertWebController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public String listAlerts(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             Model model) {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;

        // Fetch alerts sorted by triggered_at descending (newest first) then id
        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
        Page<AlertDto> alertPage = alertService.getAllAlerts(pageable);
        int lastPage = Math.max(0, alertPage.getTotalPages() - 1);
        if (page > lastPage) alertPage = alertService.getAllAlerts(PageRequest.of(lastPage, size));
        model.addAttribute("alertPage", alertPage);
        return "alerts/list";
    }
}
