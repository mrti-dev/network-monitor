package com.network.network_monitor.controller;

import java.util.List;

import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.repository.AlertRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/alerts")
public class AlertWebController {

    private final AlertRepository alertRepository;

    @Autowired
    public AlertWebController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public String listAlerts(Model model) {
        // Fetch all alerts sorted by triggered_at descending (newest first)
        List<Alert> alerts = alertRepository.findAll(Sort.by(Sort.Direction.DESC, "triggeredAt"));
        model.addAttribute("alerts", alerts);
        return "alerts/list";
    }
}
