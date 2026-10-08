package com.network.network_monitor.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.network.network_monitor.dto.TcpPortFormDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.TcpPortStatus;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.notification.TcpPortStatusChangedEvent;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.TcpPortMonitorRepository;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.strategy.TcpProbeResult;

@ExtendWith(MockitoExtension.class)
class TcpPortMonitorServiceTest {

    @Mock
    private TcpPortMonitorRepository portRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private AlertService alertService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock fixedClock;
    private TcpPortMonitorServiceImpl portService;

    private Device testDevice;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneId.of("UTC"));
        portService = new TcpPortMonitorServiceImpl(portRepository, deviceRepository, alertService, eventPublisher, fixedClock);

        testDevice = Device.builder()
                .id(1L)
                .name("Core Switch")
                .ipAddress("192.168.1.1")
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build();
    }

    @Test
    void createPort_validationError_whenPortOutOfRange() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));

        TcpPortFormDto formLow = TcpPortFormDto.builder().port(0).build();
        assertThatThrownBy(() -> portService.createPort(1L, formLow))
                .isInstanceOf(IllegalArgumentException.class);

        TcpPortFormDto formHigh = TcpPortFormDto.builder().port(70000).build();
        assertThatThrownBy(() -> portService.createPort(1L, formHigh))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPort_duplicateError_whenPortAlreadyExistsOnDevice() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(portRepository.existsByDeviceIdAndPort(1L, 80)).thenReturn(true);

        TcpPortFormDto form = TcpPortFormDto.builder().port(80).build();
        assertThatThrownBy(() -> portService.createPort(1L, form))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("đã được cấu hình");
    }

    @Test
    void evaluateProbeResult_antiFlapping_andTriggerSingleAlert() {
        TcpPortMonitor monitor = TcpPortMonitor.builder()
                .id(10L)
                .device(testDevice)
                .port(80)
                .serviceName("HTTP Web")
                .enabled(true)
                .failureThreshold(3)
                .successThreshold(2)
                .status(TcpPortStatus.UP)
                .consecutiveFailures(0)
                .consecutiveSuccesses(0)
                .build();

        when(portRepository.findById(10L)).thenReturn(Optional.of(monitor));

        LocalDateTime checkTime = LocalDateTime.now(fixedClock);
        TcpProbeResult failResult = TcpProbeResult.failure("Connection refused", checkTime);

        // Lần fail 1: chưa đạt ngưỡng (1/3)
        portService.evaluateProbeResult(10L, failResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.UP);
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(1);
        verify(alertService, never()).triggerPortAlert(any(), any(), any(), any());

        // Lần fail 2: chưa đạt ngưỡng (2/3)
        portService.evaluateProbeResult(10L, failResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.UP);
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(2);
        verify(alertService, never()).triggerPortAlert(any(), any(), any(), any());

        // Lần fail 3: đạt ngưỡng (3/3) -> chuyển DOWN và trigger 1 alert
        portService.evaluateProbeResult(10L, failResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.DOWN);
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(3);
        verify(alertService, times(1)).triggerPortAlert(eq(testDevice), eq(80), eq("HTTP Web"), eq("Connection refused"));
        verify(eventPublisher, times(1)).publishEvent(any(TcpPortStatusChangedEvent.class));

        // Lần fail 4: tiếp tục fail -> không trigger alert trùng
        portService.evaluateProbeResult(10L, failResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.DOWN);
        assertThat(monitor.getConsecutiveFailures()).isEqualTo(4);
        verify(alertService, times(1)).triggerPortAlert(any(), any(), any(), any()); // Không tăng số lần gọi
    }

    @Test
    void evaluateProbeResult_recoverDownToUp_resolvesAlertCorrectly() {
        TcpPortMonitor monitor = TcpPortMonitor.builder()
                .id(10L)
                .device(testDevice)
                .port(80)
                .serviceName("HTTP Web")
                .enabled(true)
                .failureThreshold(3)
                .successThreshold(2)
                .status(TcpPortStatus.DOWN)
                .consecutiveFailures(3)
                .consecutiveSuccesses(0)
                .build();

        when(portRepository.findById(10L)).thenReturn(Optional.of(monitor));

        LocalDateTime checkTime = LocalDateTime.now(fixedClock);
        TcpProbeResult okResult = TcpProbeResult.success(12.5, checkTime);

        // Lần success 1: chưa đạt ngưỡng (1/2)
        portService.evaluateProbeResult(10L, okResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.DOWN);
        assertThat(monitor.getConsecutiveSuccesses()).isEqualTo(1);
        verify(alertService, never()).resolvePortAlert(any(), any());

        // Lần success 2: đạt ngưỡng (2/2) -> chuyển UP và resolve alert
        portService.evaluateProbeResult(10L, okResult);
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.UP);
        assertThat(monitor.getConsecutiveSuccesses()).isEqualTo(2);
        verify(alertService, times(1)).resolvePortAlert(eq(testDevice), eq(80));
        verify(eventPublisher, times(1)).publishEvent(argThat((Object event) ->
                event instanceof TcpPortStatusChangedEvent e
                        && e.port() == 80 && e.currentStatus() == TcpPortStatus.UP));
    }

    @Test
    void evaluateProbeResult_skip_whenDeviceInMaintenance() {
        testDevice.setStatus(DeviceStatus.MAINTENANCE);
        TcpPortMonitor monitor = TcpPortMonitor.builder()
                .id(10L)
                .device(testDevice)
                .port(80)
                .enabled(true)
                .status(TcpPortStatus.UP)
                .build();

        when(portRepository.findById(10L)).thenReturn(Optional.of(monitor));

        TcpProbeResult failResult = TcpProbeResult.failure("Connection refused", LocalDateTime.now(fixedClock));
        portService.evaluateProbeResult(10L, failResult);

        // Không xử lý probe, không đổi trạng thái hay tạo alert khi MAINTENANCE
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.UP);
        verify(alertService, never()).triggerPortAlert(any(), any(), any(), any());
        verify(portRepository, never()).save(any());
    }

    @Test
    void evaluateProbeResult_skip_whenPortDisabled() {
        TcpPortMonitor monitor = TcpPortMonitor.builder()
                .id(10L)
                .device(testDevice)
                .port(80)
                .enabled(false)
                .status(TcpPortStatus.UP)
                .build();

        when(portRepository.findById(10L)).thenReturn(Optional.of(monitor));

        TcpProbeResult failResult = TcpProbeResult.failure("Connection refused", LocalDateTime.now(fixedClock));
        portService.evaluateProbeResult(10L, failResult);

        // Bỏ qua port bị disabled
        assertThat(monitor.getStatus()).isEqualTo(TcpPortStatus.UP);
        verify(alertService, never()).triggerPortAlert(any(), any(), any(), any());
        verify(portRepository, never()).save(any());
    }
}
