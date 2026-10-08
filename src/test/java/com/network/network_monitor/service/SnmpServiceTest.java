package com.network.network_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.network.network_monitor.dto.SnmpConfigDto;
import com.network.network_monitor.dto.SnmpConfigFormDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.entity.SnmpMetricLog;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.SnmpVersion;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.repository.SnmpInterfaceMetricRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.service.impl.SnmpServiceImpl;
import com.network.network_monitor.strategy.SnmpProbeResult;
import com.network.network_monitor.strategy.SnmpProbingStrategy;
import com.network.network_monitor.util.SnmpCredentialEncryptor;

@ExtendWith(MockitoExtension.class)
class SnmpServiceTest {

    @Mock
    private SnmpConfigRepository snmpConfigRepository;
    @Mock
    private SnmpMetricLogRepository metricLogRepository;
    @Mock
    private SnmpInterfaceMetricRepository interfaceMetricRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private SnmpProbingStrategy probingStrategy;
    @Mock
    private SnmpCredentialEncryptor credentialEncryptor;
    @Mock
    private AlertService alertService;
    @Mock
    private ApplicationEventPublisher events;

    private SnmpServiceImpl snmpService;

    private Device testDevice;

    @BeforeEach
    void setUp() {
        snmpService = new SnmpServiceImpl(
                snmpConfigRepository,
                metricLogRepository,
                interfaceMetricRepository,
                deviceRepository,
                probingStrategy,
                credentialEncryptor,
                alertService,
                events
        );

        testDevice = Device.builder()
                .id(1L)
                .name("Core-Switch")
                .ipAddress("192.168.1.1")
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .isDeleted(false)
                .build();
    }

    @Test
    void saveConfig_newConfig_encryptsCommunity() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(snmpConfigRepository.findByDeviceId(1L)).thenReturn(Optional.empty());
        when(credentialEncryptor.encrypt("secret-comm")).thenReturn("encrypted-comm");
        when(snmpConfigRepository.save(any(SnmpConfig.class))).thenAnswer(i -> {
            SnmpConfig c = i.getArgument(0);
            c.setId(10L);
            return c;
        });

        SnmpConfigFormDto form = SnmpConfigFormDto.builder()
                .version(SnmpVersion.V2C)
                .port(161)
                .community("secret-comm")
                .timeoutMs(3000)
                .retries(2)
                .enabled(true)
                .build();

        SnmpConfigDto result = snmpService.saveConfig(1L, form);

        assertThat(result).isNotNull();
        assertThat(result.getCommunity()).isEqualTo("******");
        verify(credentialEncryptor).encrypt("secret-comm");
        verify(probingStrategy).clearCounterCache(1L);
    }

    @Test
    void saveConfig_existingConfig_preservesCommunityWhenMasked() {
        SnmpConfig existing = SnmpConfig.builder()
                .id(10L)
                .device(testDevice)
                .version(SnmpVersion.V2C)
                .port(161)
                .communityEncrypted("already-encrypted")
                .timeoutMs(3000)
                .retries(2)
                .enabled(true)
                .build();

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(snmpConfigRepository.findByDeviceId(1L)).thenReturn(Optional.of(existing));
        when(snmpConfigRepository.save(any(SnmpConfig.class))).thenAnswer(i -> i.getArgument(0));

        SnmpConfigFormDto form = SnmpConfigFormDto.builder()
                .version(SnmpVersion.V2C)
                .port(1161)
                .community("******") // Masked
                .timeoutMs(4000)
                .retries(3)
                .enabled(true)
                .build();

        SnmpConfigDto result = snmpService.saveConfig(1L, form);

        assertThat(result.getPort()).isEqualTo(1161);
        assertThat(existing.getCommunityEncrypted()).isEqualTo("already-encrypted");
    }

    @Test
    void processProbeResult_reachable_savesLogsAndResolvesAlerts() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(metricLogRepository.save(any(SnmpMetricLog.class))).thenAnswer(i -> {
            SnmpMetricLog l = i.getArgument(0);
            l.setId(100L);
            return l;
        });

        SnmpProbeResult probeResult = SnmpProbeResult.builder()
                .reachable(true)
                .sysName("Switch-01")
                .sysUptime(360000L)
                .cpuUsage(45.0)
                .memoryUsage(60.0)
                .inboundBps(100000.0)
                .outboundBps(50000.0)
                .build();

        snmpService.processProbeResult(1L, probeResult);

        verify(alertService).resolveAlerts(testDevice, AlertType.SNMP_UNREACHABLE);
        verify(metricLogRepository).save(any(SnmpMetricLog.class));
    }

    @Test
    void processProbeResult_unreachable_triggersAlert() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));

        SnmpProbeResult probeResult = SnmpProbeResult.builder()
                .reachable(false)
                .failureReason("Timeout after 3000ms")
                .build();

        snmpService.processProbeResult(1L, probeResult);

        verify(alertService).triggerAlert(eq(testDevice), any(), eq(AlertType.SNMP_UNREACHABLE));
    }

    @Test
    void processProbeResult_cpuHigh_triggersOverloadAlert() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(metricLogRepository.save(any(SnmpMetricLog.class))).thenAnswer(i -> {
            SnmpMetricLog l = i.getArgument(0);
            l.setId(101L);
            return l;
        });

        SnmpProbeResult probeResult = SnmpProbeResult.builder()
                .reachable(true)
                .cpuUsage(95.5)
                .build();

        snmpService.processProbeResult(1L, probeResult);

        verify(alertService).triggerAlert(eq(testDevice), any(), eq(AlertType.CPU_OVERLOAD));
    }
}
