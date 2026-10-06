package com.network.network_monitor.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.network.network_monitor.dto.MaintenanceScheduleDto;
import com.network.network_monitor.service.MaintenanceScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MaintenanceApiController {

    private final MaintenanceScheduleService maintenanceService;

    @GetMapping("/devices/{deviceId}/maintenance/schedules")
    public ResponseEntity<List<MaintenanceScheduleDto>> getDeviceSchedules(@PathVariable Long deviceId) {
        return ResponseEntity.ok(maintenanceService.getSchedulesByDeviceId(deviceId));
    }

    @GetMapping("/maintenance/upcoming")
    public ResponseEntity<List<MaintenanceScheduleDto>> getUpcomingSchedules(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return ResponseEntity.ok(maintenanceService.getUpcomingSchedules(safeLimit));
    }
}
