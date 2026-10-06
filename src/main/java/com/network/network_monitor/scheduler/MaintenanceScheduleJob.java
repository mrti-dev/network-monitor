package com.network.network_monitor.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.network.network_monitor.service.MaintenanceScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceScheduleJob {

    private final MaintenanceScheduleService maintenanceScheduleService;

    @Scheduled(fixedDelayString = "${maintenance.scheduler.tick-ms:5000}")
    public void processSchedules() {
        try {
            maintenanceScheduleService.processDueSchedules();
        } catch (Exception e) {
            log.error("Lỗi khi xử lý lịch bảo trì định kỳ", e);
        }
    }
}
