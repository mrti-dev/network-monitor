package com.network.network_monitor.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceStatusHistory;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.notification.DeviceStatusChangedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.DeviceStatusHistoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusHistoryService {

    private final DeviceStatusHistoryRepository historyRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public void recordStatusChange(Device device, DeviceStatus newStatus, String source) {
        if (device == null || device.getId() == null || newStatus == null) return;
        LocalDateTime now = LocalDateTime.now();

        Optional<DeviceStatusHistory> openOpt = historyRepository.findOpenStatusByDeviceId(device.getId());
        if (openOpt.isPresent()) {
            DeviceStatusHistory open = openOpt.get();
            if (open.getStatus() == newStatus) {
                // Không tạo record trùng khi trạng thái không đổi
                return;
            }
            open.setEndedAt(now);
            historyRepository.save(open);
        }

        DeviceStatusHistory newRecord = DeviceStatusHistory.builder()
                .device(device)
                .status(newStatus)
                .startedAt(now)
                .endedAt(null)
                .source(source != null ? source : "SYSTEM")
                .build();
        historyRepository.save(newRecord);
    }

    @EventListener
    @Transactional
    public void onDeviceStatusChanged(DeviceStatusChangedEvent event) {
        if (event == null || event.deviceId() == null) return;
        Device device = deviceRepository.findById(event.deviceId()).orElse(null);
        if (device != null) {
            recordStatusChange(device, event.currentStatus(), "EVENT:" + (event.reason() != null ? event.reason() : "STATUS_CHANGED"));
        }
    }
}
