package com.network.network_monitor.service;

import java.util.List;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;

public interface DeviceService {
    List<DeviceResponseDto> getAllDevices();
    DeviceFormDto getDeviceFormById(Long id);
    void saveDevice(DeviceFormDto formDto);
    void deleteDevice(Long id);
    void toggleMonitoring(Long id);
}
