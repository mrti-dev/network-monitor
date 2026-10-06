package com.network.network_monitor.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.network.network_monitor.dto.DeviceFilterDto;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.dto.MonitoringUpdateDto;

public interface DeviceService {
    Page<DeviceResponseDto> getAllDevices(Pageable pageable);
    Page<DeviceResponseDto> searchDevices(DeviceFilterDto filter, Pageable pageable);
    List<MetricLogDto> getDeviceMetrics(Long deviceId);
    Double getLatencyThreshold(Long deviceId);
    DeviceFormDto getDeviceFormById(Long id);
    void saveDevice(DeviceFormDto formDto);
    void updateMonitoringConfig(MonitoringUpdateDto updateDto);
    void deleteDevice(Long id);
    void toggleMonitoring(Long id);
    void toggleMaintenanceMode(Long id);
}
