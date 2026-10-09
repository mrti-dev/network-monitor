package com.network.network_monitor.service;

import java.time.LocalDateTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceStatusHistory;
import com.network.network_monitor.entity.MetricLog;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.DeviceStatusHistoryRepository;
import com.network.network_monitor.repository.MetricLogRepository;

import lombok.RequiredArgsConstructor;
@Component
@Profile("sla-demo")
@RequiredArgsConstructor
public class DemoSlaDataSeeder implements ApplicationRunner {

    public static final String SIMULATED_DATA_SOURCE = "SIMULATED_DEMO_DATA";

    private final DeviceRepository deviceRepository;
    private final DeviceStatusHistoryRepository deviceStatusHistoryRepository;
    private final MetricLogRepository metricLogRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("198.51.100.1", "[DEMO SLA] Core Switch", DeviceType.SWITCH, 45);
        seed("198.51.100.2", "[DEMO SLA] Edge Router", DeviceType.ROUTER, 150);
    }

    private void seed(String ipAddress, String name, DeviceType type, int outageMinutes) {
        if (deviceRepository.findByIpAddressIncludingDeleted(ipAddress).isPresent()) return;

        Device device = deviceRepository.save(Device.builder()
                .name(name)
                .ipAddress(ipAddress)
                .deviceType(type)
                .status(DeviceStatus.ONLINE)
                .isMonitored(false)
                .location("Dữ liệu mô phỏng SLA")
                .build());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime outageStart = now.minusDays(5).withHour(2).withMinute(0);
        LocalDateTime outageEnd = outageStart.plusMinutes(outageMinutes);
        saveStatus(device, DeviceStatus.ONLINE, now.minusDays(30), outageStart);
        saveStatus(device, DeviceStatus.OFFLINE, outageStart, outageEnd);
        saveStatus(device, DeviceStatus.ONLINE, outageEnd, null);

        for (int i = 0; i < 24; i++) {
            metricLogRepository.save(MetricLog.builder()
                    .device(device)
                    .isReachable(true)
                    .latencyMs(12.5 + i % 5)
                    .packetLoss(0.0)
                    .recordedAt(now.minusHours(i))
                    .build());
        }
    }

    private void saveStatus(Device device, DeviceStatus status, LocalDateTime start, LocalDateTime end) {
        deviceStatusHistoryRepository.save(DeviceStatusHistory.builder()
                .device(device)
                .status(status)
                .startedAt(start)
                .endedAt(end)
                .source(SIMULATED_DATA_SOURCE)
                .build());
    }
}
