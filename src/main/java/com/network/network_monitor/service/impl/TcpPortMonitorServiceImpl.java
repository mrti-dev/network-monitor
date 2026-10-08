package com.network.network_monitor.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.TcpPortDto;
import com.network.network_monitor.dto.TcpPortFormDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.TcpPortStatus;
import com.network.network_monitor.enums.ServiceType;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.notification.TcpPortStatusChangedEvent;
import com.network.network_monitor.notification.ServiceAvailabilityRecordedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.TcpPortMonitorRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.service.TcpPortMonitorService;
import com.network.network_monitor.strategy.TcpProbeResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TcpPortMonitorServiceImpl implements TcpPortMonitorService {

    private final TcpPortMonitorRepository portRepository;
    private final DeviceRepository deviceRepository;
    private final AlertService alertService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public TcpPortMonitorServiceImpl(TcpPortMonitorRepository portRepository,
                                     DeviceRepository deviceRepository,
                                     AlertService alertService,
                                     ApplicationEventPublisher events,
                                     @Autowired(required = false) Clock clock) {
        this.portRepository = portRepository;
        this.deviceRepository = deviceRepository;
        this.alertService = alertService;
        this.events = events;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TcpPortDto> getPortsByDeviceId(Long deviceId) {
        return portRepository.findByDeviceIdOrderByPortAsc(deviceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TcpPortDto getPortById(Long id) {
        return portRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình port với ID: " + id));
    }

    @Override
    @Transactional
    public TcpPortDto createPort(Long deviceId, TcpPortFormDto form) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + deviceId));

        if (form.getPort() == null || form.getPort() < 1 || form.getPort() > 65535) {
            throw new IllegalArgumentException("Cổng TCP phải trong khoảng từ 1 đến 65535");
        }

        if (portRepository.existsByDeviceIdAndPort(deviceId, form.getPort())) {
            throw new DuplicateResourceException("Cổng " + form.getPort() + " đã được cấu hình cho thiết bị này");
        }

        int failureThresh = form.getFailureThreshold() != null && form.getFailureThreshold() > 0 ? form.getFailureThreshold() : 3;
        int successThresh = form.getSuccessThreshold() != null && form.getSuccessThreshold() > 0 ? form.getSuccessThreshold() : 2;
        int timeout = form.getTimeoutMs() != null && form.getTimeoutMs() >= 100 && form.getTimeoutMs() <= 30000 ? form.getTimeoutMs() : 3000;
        boolean enabled = form.getEnabled() != null ? form.getEnabled() : true;

        TcpPortMonitor monitor = TcpPortMonitor.builder()
                .device(device)
                .port(form.getPort())
                .serviceName(form.getServiceName())
                .enabled(enabled)
                .timeoutMs(timeout)
                .failureThreshold(failureThresh)
                .successThreshold(successThresh)
                .status(TcpPortStatus.UNKNOWN)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .build();

        TcpPortMonitor saved = portRepository.save(monitor);
        return toDto(saved);
    }

    @Override
    @Transactional
    public TcpPortDto updatePort(Long id, TcpPortFormDto form) {
        TcpPortMonitor monitor = portRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình port với ID: " + id));

        if (form.getPort() != null && !form.getPort().equals(monitor.getPort())) {
            if (form.getPort() < 1 || form.getPort() > 65535) {
                throw new IllegalArgumentException("Cổng TCP phải trong khoảng từ 1 đến 65535");
            }
            if (portRepository.existsByDeviceIdAndPortAndIdNot(monitor.getDevice().getId(), form.getPort(), id)) {
                throw new DuplicateResourceException("Cổng " + form.getPort() + " đã tồn tại trên thiết bị");
            }
            monitor.setPort(form.getPort());
        }

        if (form.getServiceName() != null) {
            monitor.setServiceName(form.getServiceName());
        }
        if (form.getTimeoutMs() != null) {
            if (form.getTimeoutMs() < 100 || form.getTimeoutMs() > 30000) {
                throw new IllegalArgumentException("Timeout phải từ 100ms đến 30000ms");
            }
            monitor.setTimeoutMs(form.getTimeoutMs());
        }
        if (form.getFailureThreshold() != null) {
            if (form.getFailureThreshold() <= 0) {
                throw new IllegalArgumentException("Ngưỡng lỗi phải lớn hơn 0");
            }
            monitor.setFailureThreshold(form.getFailureThreshold());
        }
        if (form.getSuccessThreshold() != null) {
            if (form.getSuccessThreshold() <= 0) {
                throw new IllegalArgumentException("Ngưỡng phục hồi phải lớn hơn 0");
            }
            monitor.setSuccessThreshold(form.getSuccessThreshold());
        }
        if (form.getEnabled() != null) {
            monitor.setEnabled(form.getEnabled());
        }

        return toDto(portRepository.save(monitor));
    }

    @Override
    @Transactional
    public void togglePort(Long id) {
        TcpPortMonitor monitor = portRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình port với ID: " + id));
        monitor.setEnabled(!Boolean.TRUE.equals(monitor.getEnabled()));
        portRepository.save(monitor);
    }

    @Override
    @Transactional
    public void deletePort(Long id) {
        TcpPortMonitor monitor = portRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình port với ID: " + id));
        alertService.resolvePortAlert(monitor.getDevice(), monitor.getPort());
        portRepository.delete(monitor);
    }

    @Override
    @Transactional
    public void evaluateProbeResult(Long monitorId, TcpProbeResult result) {
        TcpPortMonitor monitor = portRepository.findById(monitorId).orElse(null);
        if (monitor == null || !Boolean.TRUE.equals(monitor.getEnabled())) {
            return;
        }

        Device device = monitor.getDevice();
        if (device == null || !Boolean.TRUE.equals(device.getIsMonitored())
                || device.getStatus() == DeviceStatus.MAINTENANCE) {
            return;
        }

        LocalDateTime now = result.checkedAt() != null ? result.checkedAt() : LocalDateTime.now(clock);
        monitor.setLastCheckedAt(now);
        events.publishEvent(new ServiceAvailabilityRecordedEvent(
                device.getId(), ServiceType.TCP, monitor.getId(), result.reachable(), now));

        if (result.reachable()) {
            monitor.setConsecutiveFailures(0);
            int succ = monitor.getConsecutiveSuccesses() != null ? monitor.getConsecutiveSuccesses() + 1 : 1;
            monitor.setConsecutiveSuccesses(succ);

            if (succ >= monitor.getSuccessThreshold() && monitor.getStatus() != TcpPortStatus.UP) {
                TcpPortStatus prev = monitor.getStatus();
                monitor.setStatus(TcpPortStatus.UP);
                monitor.setLastStatusChangedAt(now);
                alertService.resolvePortAlert(device, monitor.getPort());
                events.publishEvent(new TcpPortStatusChangedEvent(
                        monitor.getId(),
                        device.getId(),
                        device.getName(),
                        device.getIpAddress(),
                        monitor.getPort(),
                        monitor.getServiceName(),
                        prev,
                        TcpPortStatus.UP,
                        result.latencyMs(),
                        result.reason(),
                        now
                ));
            }
        } else {
            monitor.setConsecutiveSuccesses(0);
            int fail = monitor.getConsecutiveFailures() != null ? monitor.getConsecutiveFailures() + 1 : 1;
            monitor.setConsecutiveFailures(fail);

            if (fail >= monitor.getFailureThreshold() && monitor.getStatus() != TcpPortStatus.DOWN) {
                TcpPortStatus prev = monitor.getStatus();
                monitor.setStatus(TcpPortStatus.DOWN);
                monitor.setLastStatusChangedAt(now);
                alertService.triggerPortAlert(device, monitor.getPort(), monitor.getServiceName(), result.reason());
                events.publishEvent(new TcpPortStatusChangedEvent(
                        monitor.getId(),
                        device.getId(),
                        device.getName(),
                        device.getIpAddress(),
                        monitor.getPort(),
                        monitor.getServiceName(),
                        prev,
                        TcpPortStatus.DOWN,
                        0.0,
                        result.reason(),
                        now
                ));
            }
        }

        portRepository.save(monitor);
    }

    private TcpPortDto toDto(TcpPortMonitor entity) {
        Device d = entity.getDevice();
        return TcpPortDto.builder()
                .id(entity.getId())
                .deviceId(d != null ? d.getId() : null)
                .deviceName(d != null ? d.getName() : null)
                .ipAddress(d != null ? d.getIpAddress() : null)
                .port(entity.getPort())
                .serviceName(entity.getServiceName())
                .enabled(entity.getEnabled())
                .timeoutMs(entity.getTimeoutMs())
                .failureThreshold(entity.getFailureThreshold())
                .successThreshold(entity.getSuccessThreshold())
                .status(entity.getStatus())
                .consecutiveFailures(entity.getConsecutiveFailures())
                .consecutiveSuccesses(entity.getConsecutiveSuccesses())
                .lastCheckedAt(entity.getLastCheckedAt())
                .lastStatusChangedAt(entity.getLastStatusChangedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
