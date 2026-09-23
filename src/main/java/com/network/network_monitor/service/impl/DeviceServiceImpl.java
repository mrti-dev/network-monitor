package com.network.network_monitor.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.ProbingMethod;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.DeviceService;

import com.network.network_monitor.scheduler.HealthEvaluator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final HealthEvaluator healthEvaluator;

    @Override
    public List<DeviceResponseDto> getAllDevices() {
        return deviceRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public DeviceFormDto getDeviceFormById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        return mapToFormDto(device);
    }

    @Override
    @Transactional
    public void saveDevice(DeviceFormDto formDto) {
        if (formDto.getId() == null) {
            // Create new
            if (deviceRepository.existsByIpAddress(formDto.getIpAddress())) {
                throw new DuplicateResourceException("IP " + formDto.getIpAddress() + " đã tồn tại trong hệ thống.");
            }
            Device device = Device.builder()
                    .name(formDto.getName())
                    .ipAddress(formDto.getIpAddress())
                    .macAddress(formDto.getMacAddress())
                    .deviceType(formDto.getDeviceType())
                    .location(formDto.getLocation())
                    .status(DeviceStatus.UNKNOWN)
                    .isMonitored(true)
                    .isDeleted(false)
                    .build();

            MonitoringConfig config = MonitoringConfig.builder()
                    .device(device)
                    .pingInterval(15)
                    .timeoutMs(2000)
                    .latencyThreshold(150.0)
                    .strategyType(ProbingMethod.ICMP)
                    .build();
            device.setMonitoringConfig(config);

            deviceRepository.save(device);
        } else {
            // Update
            Device device = deviceRepository.findById(formDto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + formDto.getId()));

            if (!device.getIpAddress().equals(formDto.getIpAddress()) && deviceRepository.existsByIpAddress(formDto.getIpAddress())) {
                throw new DuplicateResourceException("IP " + formDto.getIpAddress() + " đã tồn tại trong hệ thống.");
            }

            device.setName(formDto.getName());
            if (!device.getIpAddress().equals(formDto.getIpAddress())) {
                healthEvaluator.resetCounters(device.getId());
                device.setIpAddress(formDto.getIpAddress());
            }
            device.setMacAddress(formDto.getMacAddress());
            device.setDeviceType(formDto.getDeviceType());
            device.setLocation(formDto.getLocation());

            deviceRepository.save(device);
        }
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id);
        }
        healthEvaluator.resetCounters(id);
        deviceRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void toggleMonitoring(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id));
        device.setIsMonitored(!device.getIsMonitored());
        if (!device.getIsMonitored()) {
            healthEvaluator.resetCounters(id);
        }
        deviceRepository.save(device);
    }

    private DeviceResponseDto mapToResponseDto(Device device) {
        return DeviceResponseDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .status(device.getStatus())
                .location(device.getLocation())
                .isMonitored(device.getIsMonitored())
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    private DeviceFormDto mapToFormDto(Device device) {
        return DeviceFormDto.builder()
                .id(device.getId())
                .name(device.getName())
                .ipAddress(device.getIpAddress())
                .macAddress(device.getMacAddress())
                .deviceType(device.getDeviceType())
                .location(device.getLocation())
                .build();
    }
}
