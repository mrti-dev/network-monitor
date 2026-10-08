package com.network.network_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceGroup;
import com.network.network_monitor.entity.DeviceStatusHistory;
import com.network.network_monitor.entity.SlaTarget;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.SlaPeriod;
import com.network.network_monitor.enums.SlaStatus;
import com.network.network_monitor.enums.TcpPortStatus;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.DeviceStatusHistoryRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.repository.SlaTargetRepository;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.repository.ServiceAvailabilityLogRepository;
import com.network.network_monitor.repository.TcpPortMonitorRepository;
import com.network.network_monitor.service.impl.SlaReportServiceImpl;

@ExtendWith(MockitoExtension.class)
class SlaReportServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private MetricLogRepository metricLogRepository;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private SlaTargetRepository slaTargetRepository;
    @Mock
    private DeviceStatusHistoryRepository statusHistoryRepository;
    @Mock
    private TcpPortMonitorRepository tcpPortMonitorRepository;
    @Mock
    private SnmpConfigRepository snmpConfigRepository;
    @Mock
    private SnmpMetricLogRepository snmpMetricLogRepository;
    @Mock
    private ServiceAvailabilityLogRepository availabilityLogRepository;

    private SlaReportServiceImpl slaReportService;

    private Device router;
    private Device server;
    private DeviceGroup infraGroup;

    @BeforeEach
    void setUp() {
        slaReportService = new SlaReportServiceImpl(
                deviceRepository,
                metricLogRepository,
                alertRepository,
                slaTargetRepository,
                statusHistoryRepository,
                tcpPortMonitorRepository,
                snmpConfigRepository,
                snmpMetricLogRepository,
                availabilityLogRepository
        );

        infraGroup = DeviceGroup.builder().id(100L).name("Infrastructure").build();

        router = Device.builder().id(1L).name("Core-Router").ipAddress("192.168.1.1")
                .deviceType(DeviceType.ROUTER).status(DeviceStatus.ONLINE).group(infraGroup).isMonitored(true).isDeleted(false).build();
        server = Device.builder().id(2L).name("App-Server").ipAddress("192.168.1.10")
                .deviceType(DeviceType.SERVER).status(DeviceStatus.ONLINE).group(infraGroup).isMonitored(true).isDeleted(false).build();
    }

    @Test
    void uptimeCalculatedByDuration_andExcludesMaintenance() {
        when(deviceRepository.findAll()).thenReturn(List.of(router));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(slaTargetRepository.findByDeviceId(1L)).thenReturn(Optional.empty());
        when(slaTargetRepository.findByGroupId(100L)).thenReturn(Optional.empty());
        when(tcpPortMonitorRepository.findByDeviceId(1L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(1L)).thenReturn(Optional.empty());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(10);

        // History:
        // 1. Online: from - 1h to from + 5h
        // 2. Maintenance: from + 5h to from + 7h (2 hours maintenance = 7200s)
        // 3. Offline during maintenance + after: from + 6h to from + 8h (2 hours offline, 1 hour during maintenance => net downtime = 1 hour = 3600s)
        // 4. Online: from + 8h to now (2 hours)
        DeviceStatusHistory h1 = DeviceStatusHistory.builder()
                .device(router).status(DeviceStatus.ONLINE)
                .startedAt(from.minusHours(1)).endedAt(from.plusHours(5)).build();
        DeviceStatusHistory hMaint = DeviceStatusHistory.builder()
                .device(router).status(DeviceStatus.MAINTENANCE)
                .startedAt(from.plusHours(5)).endedAt(from.plusHours(7)).build();
        DeviceStatusHistory hOffline = DeviceStatusHistory.builder()
                .device(router).status(DeviceStatus.OFFLINE)
                .startedAt(from.plusHours(6)).endedAt(from.plusHours(8)).build();
        DeviceStatusHistory h2 = DeviceStatusHistory.builder()
                .device(router).status(DeviceStatus.ONLINE)
                .startedAt(from.plusHours(8)).endedAt(now).build();

        when(statusHistoryRepository.findOverlappingHistory(eq(1L), any(), any()))
                .thenReturn(List.of(h1, hMaint, hOffline, h2));

        SlaFilterDto filter = SlaFilterDto.builder().period(SlaPeriod.TODAY).build();
        SlaSummaryReportDto report = slaReportService.generateReport(filter);

        DeviceSlaDto dto = report.getDeviceSlaList().get(0);
        assertThat(dto.getUptimePercentage()).isNotNull();

        long reportSec = java.time.Duration.between(report.getFromDateTime(), report.getToDateTime()).toSeconds();
        long eligibleSec = reportSec - 7200; // Trừ 2h bảo trì
        double expectedUptime = Math.round(((double)(eligibleSec - 3600) / eligibleSec * 100.0) * 100.0) / 100.0;
        double expectedMttf = Math.round(((eligibleSec - 3600) / 60.0) * 10.0) / 10.0;

        assertThat(dto.getDowntimeMinutes()).isEqualTo(60.0);
        assertThat(dto.getTotalOutages()).isEqualTo(1);
        assertThat(dto.getMttrMinutes()).isEqualTo(60.0);
        assertThat(dto.getMttfMinutes()).isEqualTo(expectedMttf);
        assertThat(dto.getUptimePercentage()).isEqualTo(expectedUptime);
    }

    @Test
    void missingStatusHistory_returnsNullForUptime_displayedAsNA() {
        when(deviceRepository.findAll()).thenReturn(List.of(server));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(2L), any(), any())).thenReturn(Collections.emptyList());
        when(slaTargetRepository.findByDeviceId(2L)).thenReturn(Optional.empty());
        when(slaTargetRepository.findByGroupId(100L)).thenReturn(Optional.empty());
        when(tcpPortMonitorRepository.findByDeviceId(2L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(2L)).thenReturn(Optional.empty());

        SlaSummaryReportDto report = slaReportService.generateReport(null);

        DeviceSlaDto dto = report.getDeviceSlaList().get(0);
        assertThat(dto.getUptimePercentage()).isNull(); // N/A
        assertThat(dto.getMttrMinutes()).isNull(); // N/A
        assertThat(dto.getMttfMinutes()).isNull(); // N/A
        assertThat(dto.getSlaStatus()).isEqualTo(SlaStatus.NOT_AVAILABLE);
    }

    @Test
    void statusFilter_isAppliedAfterSlaCalculation() {
        when(deviceRepository.findAll()).thenReturn(List.of(server));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(2L), any(), any())).thenReturn(Collections.emptyList());
        when(slaTargetRepository.findByDeviceId(2L)).thenReturn(Optional.empty());
        when(slaTargetRepository.findByGroupId(100L)).thenReturn(Optional.empty());
        when(tcpPortMonitorRepository.findByDeviceId(2L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(2L)).thenReturn(Optional.empty());

        SlaFilterDto filter = SlaFilterDto.builder()
                .period(SlaPeriod.LAST_7_DAYS)
                .status(SlaStatus.MET)
                .build();

        assertThat(slaReportService.generateReport(filter).getDeviceSlaList()).isEmpty();
    }

    @Test
    void targetPriority_deviceTargetOverridesGroupTarget() {
        when(deviceRepository.findAll()).thenReturn(List.of(router));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(tcpPortMonitorRepository.findByDeviceId(1L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(1L)).thenReturn(Optional.empty());

        // Device target 99.9% vs Group target 99.0%
        SlaTarget devTarget = SlaTarget.builder().targetUptimePercent(99.9).maxLatencyMs(50.0).build();
        when(slaTargetRepository.findByDeviceId(1L)).thenReturn(Optional.of(devTarget));

        SlaSummaryReportDto report = slaReportService.generateReport(null);
        DeviceSlaDto dto = report.getDeviceSlaList().get(0);

        assertThat(dto.getTargetUptimePercentage()).isEqualTo(99.9);
    }

    @Test
    void targetPriority_groupTargetUsedWhenNoDeviceTarget() {
        when(deviceRepository.findAll()).thenReturn(List.of(router));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(1L), any(), any())).thenReturn(Collections.emptyList());
        when(tcpPortMonitorRepository.findByDeviceId(1L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(1L)).thenReturn(Optional.empty());

        when(slaTargetRepository.findByDeviceId(1L)).thenReturn(Optional.empty());
        SlaTarget groupTarget = SlaTarget.builder().targetUptimePercent(98.5).maxLatencyMs(120.0).build();
        when(slaTargetRepository.findByGroupId(100L)).thenReturn(Optional.of(groupTarget));

        SlaSummaryReportDto report = slaReportService.generateReport(null);
        DeviceSlaDto dto = report.getDeviceSlaList().get(0);

        assertThat(dto.getTargetUptimePercentage()).isEqualTo(98.5);
    }

    @Test
    void tcpAndSnmpAvailability_calculatedCorrectly() {
        when(deviceRepository.findAll()).thenReturn(List.of(server));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(2L), any(), any())).thenReturn(Collections.emptyList());
        when(slaTargetRepository.findByDeviceId(2L)).thenReturn(Optional.empty());
        when(slaTargetRepository.findByGroupId(100L)).thenReturn(Optional.empty());

        // TCP và SNMP đều được tính từ các mẫu trong kỳ báo cáo.
        TcpPortMonitor p1 = TcpPortMonitor.builder().port(80).status(TcpPortStatus.UP).enabled(true).build();
        TcpPortMonitor p2 = TcpPortMonitor.builder().port(443).status(TcpPortStatus.DOWN).enabled(true).build();
        when(tcpPortMonitorRepository.findByDeviceId(2L)).thenReturn(List.of(p1, p2));
        when(availabilityLogRepository.aggregateAvailability(eq(2L), eq(com.network.network_monitor.enums.ServiceType.TCP), any(), any()))
                .thenReturn(new Object[]{10L, 5L});

        // SNMP: enabled with 1 log
        SnmpConfig snmpConfig = SnmpConfig.builder().enabled(true).build();
        when(snmpConfigRepository.findByDeviceId(2L)).thenReturn(Optional.of(snmpConfig));
        when(availabilityLogRepository.aggregateAvailability(eq(2L), eq(com.network.network_monitor.enums.ServiceType.SNMP), any(), any()))
                .thenReturn(new Object[]{10L, 10L});

        SlaSummaryReportDto report = slaReportService.generateReport(null);
        DeviceSlaDto dto = report.getDeviceSlaList().get(0);

        assertThat(dto.getTcpAvailabilityPercentage()).isEqualTo(50.0);
        assertThat(dto.getSnmpAvailabilityPercentage()).isEqualTo(100.0);
    }

    @Test
    void customDateValidation_invalidDatesThrowException() {
        // fromDate > toDate
        SlaFilterDto invalidRange = SlaFilterDto.builder()
                .period(SlaPeriod.CUSTOM)
                .fromDate(LocalDate.now().plusDays(1))
                .toDate(LocalDate.now())
                .build();

        assertThatThrownBy(() -> slaReportService.generateReport(invalidRange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được lớn hơn");

        // Period > 365 days
        SlaFilterDto tooLongRange = SlaFilterDto.builder()
                .period(SlaPeriod.CUSTOM)
                .fromDate(LocalDate.now().minusDays(400))
                .toDate(LocalDate.now())
                .build();

        assertThatThrownBy(() -> slaReportService.generateReport(tooLongRange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vượt quá 365 ngày");

        // Null dates for CUSTOM
        SlaFilterDto nullDates = SlaFilterDto.builder()
                .period(SlaPeriod.CUSTOM)
                .fromDate(null)
                .toDate(null)
                .build();

        assertThatThrownBy(() -> slaReportService.generateReport(nullDates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vui lòng chọn đầy đủ");
    }

    @Test
    void exportCsv_escapesFormulaInjectionAndIncludesBom() {
        Device evilDevice = Device.builder()
                .id(99L)
                .name("=1+1")
                .ipAddress("+192.168.1.99")
                .deviceType(DeviceType.SERVER)
                .isMonitored(true)
                .isDeleted(false)
                .build();

        when(deviceRepository.findAll()).thenReturn(List.of(evilDevice));
        when(metricLogRepository.aggregateMetricsByPeriod(any(), any())).thenReturn(Collections.emptyList());
        when(statusHistoryRepository.findOverlappingHistory(eq(99L), any(), any())).thenReturn(Collections.emptyList());
        when(slaTargetRepository.findByDeviceId(99L)).thenReturn(Optional.empty());
        when(tcpPortMonitorRepository.findByDeviceId(99L)).thenReturn(Collections.emptyList());
        when(snmpConfigRepository.findByDeviceId(99L)).thenReturn(Optional.empty());

        byte[] csv = slaReportService.exportCsv(null);

        assertThat(csv).isNotEmpty();
        // BOM check
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        String content = new String(csv, StandardCharsets.UTF_8);
        // Formula injection sanitized with single quote: '=1+1 and '+192.168.1.99
        assertThat(content).contains("'=1+1");
        assertThat(content).contains("'+192.168.1.99");
    }
}
