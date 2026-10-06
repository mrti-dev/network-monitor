package com.network.network_monitor.service;

import java.util.List;

import com.network.network_monitor.dto.MaintenanceScheduleDto;
import com.network.network_monitor.dto.MaintenanceScheduleFormDto;

public interface MaintenanceScheduleService {

    List<MaintenanceScheduleDto> getSchedulesByDeviceId(Long deviceId);

    List<MaintenanceScheduleDto> getUpcomingSchedules(int limit);

    MaintenanceScheduleDto getScheduleById(Long id);

    MaintenanceScheduleDto createSchedule(MaintenanceScheduleFormDto formDto, String username);

    MaintenanceScheduleDto updateSchedule(Long id, MaintenanceScheduleFormDto formDto, String username);

    void cancelSchedule(Long id, String username);

    void processDueSchedules();
}
