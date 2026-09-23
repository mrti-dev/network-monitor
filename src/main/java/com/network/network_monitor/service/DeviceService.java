package com.network.network_monitor.service;

import java.util.List;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.MetricLogDto;

public interface DeviceService {
    org.springframework.data.domain.Page<DeviceResponseDto> getAllDevices(org.springframework.data.domain.Pageable pageable);
    java.util.List<MetricLogDto> getDeviceMetrics(Long deviceId);
    Double getLatencyThreshold(Long deviceId);
    DeviceFormDto getDeviceFormById(Long id);
    void saveDevice(DeviceFormDto formDto);
    void deleteDevice(Long id);
    void toggleMonitoring(Long id);
}
