package com.network.network_monitor.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.ServiceAvailabilityLog;
import com.network.network_monitor.notification.ServiceAvailabilityRecordedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.ServiceAvailabilityLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ServiceAvailabilityRecorder {
    private final DeviceRepository deviceRepository;
    private final ServiceAvailabilityLogRepository repository;

    @EventListener
    @Transactional
    public void record(ServiceAvailabilityRecordedEvent event) {
        deviceRepository.findById(event.deviceId()).ifPresent(device -> repository.save(
                ServiceAvailabilityLog.builder()
                        .device(device)
                        .serviceType(event.serviceType())
                        .monitorId(event.monitorId())
                        .available(event.available())
                        .recordedAt(event.recordedAt())
                        .build()));
    }
}
