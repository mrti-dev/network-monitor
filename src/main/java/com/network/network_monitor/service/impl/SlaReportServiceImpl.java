package com.network.network_monitor.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.network.network_monitor.dto.DeviceSlaDto;
import com.network.network_monitor.dto.SlaFilterDto;
import com.network.network_monitor.dto.SlaSummaryReportDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceStatusHistory;
import com.network.network_monitor.entity.SlaTarget;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.entity.TcpPortMonitor;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.SlaPeriod;
import com.network.network_monitor.enums.SlaStatus;
import com.network.network_monitor.enums.ServiceType;
import com.network.network_monitor.enums.TcpPortStatus;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.AlertRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.DeviceStatusHistoryRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.repository.SlaTargetRepository;
import com.network.network_monitor.repository.SnmpConfigRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.repository.ServiceAvailabilityLogRepository;
import com.network.network_monitor.repository.TcpPortMonitorRepository;
import com.network.network_monitor.service.SlaReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaReportServiceImpl implements SlaReportService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final double DEFAULT_TARGET_UPTIME = 99.5;
    private static final double DEFAULT_MAX_LATENCY = 100.0;
    private static final double DEFAULT_MAX_PACKET_LOSS = 1.0;

    private final DeviceRepository deviceRepository;
    private final MetricLogRepository metricLogRepository;
    private final AlertRepository alertRepository;
    private final SlaTargetRepository slaTargetRepository;
    private final DeviceStatusHistoryRepository statusHistoryRepository;
    private final TcpPortMonitorRepository tcpPortMonitorRepository;
    private final SnmpConfigRepository snmpConfigRepository;
    private final SnmpMetricLogRepository snmpMetricLogRepository;
    private final ServiceAvailabilityLogRepository availabilityLogRepository;

    @Override
    @Transactional(readOnly = true)
    public SlaSummaryReportDto generateReport(SlaFilterDto filter) {
        final SlaFilterDto effFilter = filter != null ? filter : SlaFilterDto.builder().period(SlaPeriod.LAST_7_DAYS).build();

        LocalDateTime[] range = resolveTimeRange(effFilter);
        LocalDateTime from = range[0];
        LocalDateTime to = range[1];

        List<Device> devices = deviceRepository.findAll();
        if (effFilter.getGroupId() != null) {
            final Long targetGroupId = effFilter.getGroupId();
            devices = devices.stream()
                    .filter(d -> d.getGroup() != null && Objects.equals(d.getGroup().getId(), targetGroupId))
                    .toList();
        }
        if (effFilter.getDeviceType() != null) {
            final DeviceType targetType = effFilter.getDeviceType();
            devices = devices.stream()
                    .filter(d -> d.getDeviceType() == targetType)
                    .toList();
        }

        // Fetch aggregate metrics cho toàn bộ thiết bị
        List<Object[]> aggRows = metricLogRepository.aggregateMetricsByPeriod(from, to);
        Map<Long, Object[]> metricMap = new HashMap<>();
        for (Object[] row : aggRows) {
            if (row[0] != null) metricMap.put((Long) row[0], row);
        }

        List<DeviceSlaDto> deviceSlaList = new ArrayList<>();
        double totalUptimeSum = 0;
        int uptimeCount = 0;
        double totalLatencySum = 0;
        int latencyDeviceCount = 0;
        double totalComplianceSum = 0;
        int complianceCount = 0;

        int metCount = 0;
        int atRiskCount = 0;
        int breachedCount = 0;
        int totalOutagesSum = 0;
        double totalDowntimeMinutesSum = 0;

        for (Device d : devices) {
            Object[] agg = metricMap.get(d.getId());
            DeviceSlaDto dto = calculateDeviceSla(d, from, to, agg);
            if (effFilter.getStatus() != null && dto.getSlaStatus() != effFilter.getStatus()) {
                continue;
            }
            deviceSlaList.add(dto);

            if (dto.getUptimePercentage() != null) {
                totalUptimeSum += dto.getUptimePercentage();
                uptimeCount++;
            }
            if (dto.getAvgLatencyMs() != null) {
                totalLatencySum += dto.getAvgLatencyMs();
                latencyDeviceCount++;
            }
            if (dto.getLatencyCompliancePercentage() != null) {
                totalComplianceSum += dto.getLatencyCompliancePercentage();
                complianceCount++;
            }

            if (dto.getSlaStatus() == SlaStatus.MET) metCount++;
            else if (dto.getSlaStatus() == SlaStatus.AT_RISK) atRiskCount++;
            else if (dto.getSlaStatus() == SlaStatus.BREACHED) breachedCount++;

            if (dto.getTotalOutages() != null) {
                totalOutagesSum += dto.getTotalOutages();
            }
            if (dto.getDowntimeMinutes() != null) {
                totalDowntimeMinutesSum += dto.getDowntimeMinutes();
            }
        }

        Double overallUptime = uptimeCount > 0 ? Math.round((totalUptimeSum / uptimeCount) * 100.0) / 100.0 : null;
        Double overallLatencyCompliance = complianceCount > 0 ? Math.round((totalComplianceSum / complianceCount) * 10.0) / 10.0 : null;
        Double avgLatency = latencyDeviceCount > 0 ? Math.round((totalLatencySum / latencyDeviceCount) * 10.0) / 10.0 : null;
        Double avgMttr = totalOutagesSum > 0 ? Math.round((totalDowntimeMinutesSum / totalOutagesSum) * 10.0) / 10.0 : null;

        return SlaSummaryReportDto.builder()
                .period(effFilter.getPeriod())
                .fromDateTime(from)
                .toDateTime(to)
                .overallUptimePercentage(overallUptime)
                .overallLatencyCompliancePercentage(overallLatencyCompliance)
                .avgNetworkLatencyMs(avgLatency)
                .totalDevicesCount(devices.size())
                .metCount(metCount)
                .atRiskCount(atRiskCount)
                .breachedCount(breachedCount)
                .totalOutages(totalOutagesSum)
                .avgMttrMinutes(avgMttr)
                .deviceSlaList(deviceSlaList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceSlaDto getDeviceSla(Long deviceId, SlaFilterDto filter) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại: " + deviceId));

        final SlaFilterDto effFilter = filter != null ? filter : SlaFilterDto.builder().period(SlaPeriod.LAST_7_DAYS).build();
        LocalDateTime[] range = resolveTimeRange(effFilter);
        LocalDateTime from = range[0];
        LocalDateTime to = range[1];

        List<Object[]> aggRows = metricLogRepository.aggregateMetricsByPeriod(from, to);
        Object[] agg = aggRows.stream().filter(r -> Objects.equals(r[0], deviceId)).findFirst().orElse(null);

        return calculateDeviceSla(device, from, to, agg);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(SlaFilterDto filter) {
        SlaSummaryReportDto report = generateReport(filter);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            writer.println("BÁO CÁO ĐÁNH GIÁ CHẤT LƯỢNG DỊCH VỤ MẠNG (SLA REPORT)");
            writer.println("Thời gian từ:," + report.getFromDateTime() + ",Thời gian đến:," + report.getToDateTime());
            writer.println("Kỳ đánh giá:," + (report.getPeriod() != null ? report.getPeriod().name() : "N/A"));
            writer.println("Tổng số thiết bị:," + report.getTotalDevicesCount() + ",Đạt:," + report.getMetCount() + ",Nguy cơ:," + report.getAtRiskCount() + ",Vi phạm:," + report.getBreachedCount());
            writer.println("Uptime trung bình:," + (report.getOverallUptimePercentage() != null ? report.getOverallUptimePercentage() + "%" : "N/A"));
            writer.println();

            // Headers
            writer.println("Mã TB,Tên thiết bị,Địa chỉ IP,Loại TB,Nhóm,Target Uptime (%),Uptime thực tế (%),Trạng thái SLA,Số sự cố,Tổng downtime (phút),MTTR (phút),MTTF (phút),Độ trễ TB (ms),Độ trễ Max (ms),Tuân thủ độ trễ (%),Packet Loss TB (%),Tuân thủ Packet Loss (%),TCP Availability (%),SNMP Availability (%)");

            for (DeviceSlaDto d : report.getDeviceSlaList()) {
                writer.println(String.join(",",
                        escapeCsv(String.valueOf(d.getDeviceId())),
                        escapeCsv(d.getDeviceName()),
                        escapeCsv(d.getIpAddress()),
                        escapeCsv(d.getDeviceType() != null ? d.getDeviceType().name() : ""),
                        escapeCsv(d.getGroupName() != null ? d.getGroupName() : ""),
                        escapeCsv(d.getTargetUptimePercentage() != null ? String.valueOf(d.getTargetUptimePercentage()) : "N/A"),
                        escapeCsv(d.getUptimePercentage() != null ? String.valueOf(d.getUptimePercentage()) : "N/A"),
                        escapeCsv(d.getSlaStatus() != null ? d.getSlaStatus().name() : "N/A"),
                        escapeCsv(d.getTotalOutages() != null ? String.valueOf(d.getTotalOutages()) : "0"),
                        escapeCsv(d.getDowntimeMinutes() != null ? String.valueOf(d.getDowntimeMinutes()) : "0"),
                        escapeCsv(d.getMttrMinutes() != null ? String.valueOf(d.getMttrMinutes()) : "N/A"),
                        escapeCsv(d.getMttfMinutes() != null ? String.valueOf(d.getMttfMinutes()) : "N/A"),
                        escapeCsv(d.getAvgLatencyMs() != null ? String.valueOf(d.getAvgLatencyMs()) : "N/A"),
                        escapeCsv(d.getMaxLatencyMs() != null ? String.valueOf(d.getMaxLatencyMs()) : "N/A"),
                        escapeCsv(d.getLatencyCompliancePercentage() != null ? String.valueOf(d.getLatencyCompliancePercentage()) : "N/A"),
                        escapeCsv(d.getAvgPacketLoss() != null ? String.valueOf(d.getAvgPacketLoss()) : "N/A"),
                        escapeCsv(d.getPacketLossCompliancePercentage() != null ? String.valueOf(d.getPacketLossCompliancePercentage()) : "N/A"),
                        escapeCsv(d.getTcpAvailabilityPercentage() != null ? String.valueOf(d.getTcpAvailabilityPercentage()) : "N/A"),
                        escapeCsv(d.getSnmpAvailabilityPercentage() != null ? String.valueOf(d.getSnmpAvailabilityPercentage()) : "N/A")
                ));
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi khi kết xuất file SLA CSV: {}", e.getMessage(), e);
            throw new IllegalStateException("Lỗi khi tạo file báo cáo SLA CSV", e);
        }
    }

    private DeviceSlaDto calculateDeviceSla(Device d, LocalDateTime from, LocalDateTime to, Object[] agg) {
        // 1. Phân cấp SLA target: Device target > Group target > Default
        SlaTarget target = slaTargetRepository.findByDeviceId(d.getId())
                .or(() -> d.getGroup() != null ? slaTargetRepository.findByGroupId(d.getGroup().getId()) : Optional.empty())
                .orElse(null);

        double targetUptime = target != null ? target.getTargetUptimePercent() : DEFAULT_TARGET_UPTIME;
        double maxLatency = target != null ? target.getMaxLatencyMs() : DEFAULT_MAX_LATENCY;
        double maxLoss = target != null ? target.getMaxPacketLossPercent() : DEFAULT_MAX_PACKET_LOSS;

        // 2. Tính toán Uptime và Downtime dựa trên device_status_history theo duration
        LocalDateTime nowInZone = LocalDateTime.now(REPORT_ZONE);
        long reportDurationSec = Duration.between(from, to).toSeconds();

        List<DeviceStatusHistory> historyList = statusHistoryRepository.findOverlappingHistory(d.getId(), from, to);

        Double uptimePercentage = null;
        Double downtimeMinutes = 0.0;
        Integer totalOutages = 0;
        Double mttrMinutes = null;
        Double mttfMinutes = null;

        if (!historyList.isEmpty() && reportDurationSec > 0) {
            List<TimeInterval> maintenanceIntervals = new ArrayList<>();
            List<TimeInterval> offlineIntervals = new ArrayList<>();

            for (DeviceStatusHistory h : historyList) {
                LocalDateTime start = h.getStartedAt();
                LocalDateTime end = h.getEndedAt() != null ? h.getEndedAt() : nowInZone;

                // Cắt theo biên khoảng báo cáo
                LocalDateTime clippedStart = start.isBefore(from) ? from : start;
                LocalDateTime clippedEnd = end.isAfter(to) ? to : end;

                if (clippedEnd.isAfter(clippedStart)) {
                    TimeInterval interval = new TimeInterval(clippedStart, clippedEnd);
                    if (h.getStatus() == DeviceStatus.MAINTENANCE) {
                        maintenanceIntervals.add(interval);
                    } else if (h.getStatus() == DeviceStatus.OFFLINE) {
                        offlineIntervals.add(interval);
                    }
                }
            }

            // Hợp nhất các khoảng bảo trì giao nhau
            List<TimeInterval> mergedMaintenance = mergeIntervals(maintenanceIntervals);
            long maintenanceSec = 0;
            for (TimeInterval m : mergedMaintenance) {
                maintenanceSec += m.durationSeconds();
            }

            long eligibleDurationSec = reportDurationSec - maintenanceSec;

            if (eligibleDurationSec > 0) {
                // Hợp nhất các khoảng offline giao nhau
                List<TimeInterval> mergedOffline = mergeIntervals(offlineIntervals);

                // Loại trừ thời gian bảo trì ra khỏi downtime offline
                long totalDowntimeSec = 0;
                int incidentCount = 0;

                for (TimeInterval off : mergedOffline) {
                    List<TimeInterval> nonMaintPieces = subtractIntervals(off, mergedMaintenance);
                    for (TimeInterval piece : nonMaintPieces) {
                        if (piece.durationSeconds() > 0) {
                            totalDowntimeSec += piece.durationSeconds();
                            incidentCount++;
                        }
                    }
                }

                downtimeMinutes = Math.round((totalDowntimeSec / 60.0) * 10.0) / 10.0;
                totalOutages = incidentCount;

                double rawUptime = ((double) (eligibleDurationSec - totalDowntimeSec) / eligibleDurationSec) * 100.0;
                rawUptime = Math.max(0.0, Math.min(100.0, rawUptime));
                uptimePercentage = Math.round(rawUptime * 100.0) / 100.0;

                if (totalOutages > 0) {
                    mttrMinutes = Math.round((downtimeMinutes / totalOutages) * 10.0) / 10.0;
                    double uptimeMin = (eligibleDurationSec - totalDowntimeSec) / 60.0;
                    mttfMinutes = Math.round((uptimeMin / totalOutages) * 10.0) / 10.0;
                }
            }
        }

        // 3. Trạng thái SLA
        SlaStatus status;
        if (uptimePercentage == null) {
            status = SlaStatus.NOT_AVAILABLE;
        } else if (uptimePercentage >= targetUptime) {
            status = SlaStatus.MET;
        } else if (uptimePercentage >= (targetUptime - 0.5)) {
            status = SlaStatus.AT_RISK;
        } else {
            status = SlaStatus.BREACHED;
        }

        // 4. Metrics ICMP & Compliance
        long totalChecks = 0;
        long reachableChecks = 0;
        Double avgLatency = null;
        Double maxLatencyRecorded = null;
        Double avgLoss = null;

        if (agg != null) {
            totalChecks = agg[1] != null ? ((Number) agg[1]).longValue() : 0;
            reachableChecks = agg[2] != null ? ((Number) agg[2]).longValue() : 0;
            if (agg[3] != null) avgLatency = Math.round(((Number) agg[3]).doubleValue() * 10.0) / 10.0;
            if (agg[4] != null) maxLatencyRecorded = Math.round(((Number) agg[4]).doubleValue() * 10.0) / 10.0;
            if (agg[5] != null) avgLoss = Math.round(((Number) agg[5]).doubleValue() * 10.0) / 10.0;
        }

        Double latencyCompliance = null;
        if (reachableChecks > 0) {
            Long compliantCount = metricLogRepository.countLatencyCompliantForDevice(d.getId(), from, to, maxLatency);
            long compliant = compliantCount != null ? compliantCount : 0L;
            latencyCompliance = Math.round(((compliant * 100.0) / reachableChecks) * 10.0) / 10.0;
        }

        Double lossCompliance = null;
        if (totalChecks > 0) {
            Long compliantCount = metricLogRepository.countPacketLossCompliantForDevice(d.getId(), from, to, maxLoss);
            long compliant = compliantCount != null ? compliantCount : 0L;
            lossCompliance = Math.round(((compliant * 100.0) / totalChecks) * 10.0) / 10.0;
        }

        // 5. TCP Service Availability
        Double tcpAvailability = null;
        List<TcpPortMonitor> tcpMonitors = tcpPortMonitorRepository.findByDeviceId(d.getId());
        List<TcpPortMonitor> enabledTcpMonitors = tcpMonitors.stream().filter(TcpPortMonitor::getEnabled).toList();
        if (!enabledTcpMonitors.isEmpty()) {
            tcpAvailability = calculateAvailability(d.getId(), ServiceType.TCP, from, to);
        }

        // 6. SNMP Availability
        Double snmpAvailability = null;
        Optional<SnmpConfig> snmpConfigOpt = snmpConfigRepository.findByDeviceId(d.getId());
        if (snmpConfigOpt.isPresent() && Boolean.TRUE.equals(snmpConfigOpt.get().getEnabled())) {
            snmpAvailability = calculateAvailability(d.getId(), ServiceType.SNMP, from, to);
        }

        return DeviceSlaDto.builder()
                .deviceId(d.getId())
                .deviceName(d.getName())
                .ipAddress(d.getIpAddress())
                .deviceType(d.getDeviceType())
                .groupName(d.getGroup() != null ? d.getGroup().getName() : null)
                .totalChecks(totalChecks)
                .reachableChecks(reachableChecks)
                .unreachableChecks(totalChecks - reachableChecks)
                .uptimePercentage(uptimePercentage)
                .targetUptimePercentage(targetUptime)
                .avgLatencyMs(avgLatency)
                .maxLatencyMs(maxLatencyRecorded)
                .latencyCompliancePercentage(latencyCompliance)
                .avgPacketLoss(avgLoss)
                .packetLossCompliancePercentage(lossCompliance)
                .totalOutages(totalOutages)
                .downtimeMinutes(downtimeMinutes)
                .mttrMinutes(mttrMinutes)
                .mttfMinutes(mttfMinutes)
                .tcpAvailabilityPercentage(tcpAvailability)
                .snmpAvailabilityPercentage(snmpAvailability)
                .slaStatus(status)
                .build();
    }

    private Double calculateAvailability(Long deviceId, ServiceType serviceType,
                                         LocalDateTime from, LocalDateTime to) {
        Object[] aggregate = availabilityLogRepository.aggregateAvailability(deviceId, serviceType, from, to);
        if (aggregate == null || aggregate.length < 2 || aggregate[0] == null) return null;
        long total = ((Number) aggregate[0]).longValue();
        if (total == 0) return null;
        long available = aggregate[1] != null ? ((Number) aggregate[1]).longValue() : 0L;
        return Math.round((available * 1000.0) / total) / 10.0;
    }

    private LocalDateTime[] resolveTimeRange(SlaFilterDto filter) {
        LocalDateTime now = LocalDateTime.now(REPORT_ZONE);
        if (filter.getPeriod() == SlaPeriod.TODAY) {
            return new LocalDateTime[]{LocalDate.now(REPORT_ZONE).atStartOfDay(), now};
        } else if (filter.getPeriod() == SlaPeriod.LAST_30_DAYS) {
            return new LocalDateTime[]{now.minusDays(30), now};
        } else if (filter.getPeriod() == SlaPeriod.CUSTOM) {
            if (filter.getFromDate() == null || filter.getToDate() == null) {
                throw new IllegalArgumentException("Vui lòng chọn đầy đủ từ ngày và đến ngày cho khoảng thời gian tùy chỉnh");
            }
            if (filter.getFromDate().isAfter(filter.getToDate())) {
                throw new IllegalArgumentException("Từ ngày (fromDate) không được lớn hơn đến ngày (toDate)");
            }
            if (Duration.between(filter.getFromDate().atStartOfDay(), filter.getToDate().atTime(23, 59, 59)).toDays() > 365) {
                throw new IllegalArgumentException("Khoảng thời gian báo cáo tùy chỉnh không được vượt quá 365 ngày");
            }
            return new LocalDateTime[]{
                    filter.getFromDate().atStartOfDay(),
                    filter.getToDate().atTime(23, 59, 59)
            };
        } else {
            // Default: LAST_7_DAYS
            return new LocalDateTime[]{now.minusDays(7), now};
        }
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        String val = str;
        // Chống CSV formula injection: nếu bắt đầu bằng =, +, -, @ thì thêm nháy đơn '
        if (val.startsWith("=") || val.startsWith("+") || val.startsWith("-") || val.startsWith("@")) {
            val = "'" + val;
        }
        if (val.contains("\"") || val.contains(",") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private record TimeInterval(LocalDateTime start, LocalDateTime end) {
        long durationSeconds() {
            return Duration.between(start, end).toSeconds();
        }
    }

    private List<TimeInterval> mergeIntervals(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) return List.of();
        List<TimeInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(TimeInterval::start));

        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            TimeInterval next = sorted.get(i);
            if (!next.start().isAfter(current.end())) {
                LocalDateTime newEnd = next.end().isAfter(current.end()) ? next.end() : current.end();
                current = new TimeInterval(current.start(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<TimeInterval> subtractIntervals(TimeInterval base, List<TimeInterval> toSubtract) {
        List<TimeInterval> result = new ArrayList<>();
        result.add(base);

        for (TimeInterval sub : toSubtract) {
            List<TimeInterval> nextResult = new ArrayList<>();
            for (TimeInterval piece : result) {
                if (!sub.end().isAfter(piece.start()) || !sub.start().isBefore(piece.end())) {
                    // Không giao nhau
                    nextResult.add(piece);
                } else {
                    // Giao nhau: cắt ra phần trước và sau
                    if (sub.start().isAfter(piece.start())) {
                        nextResult.add(new TimeInterval(piece.start(), sub.start()));
                    }
                    if (sub.end().isBefore(piece.end())) {
                        nextResult.add(new TimeInterval(sub.end(), piece.end()));
                    }
                }
            }
            result = nextResult;
        }
        return result;
    }
}
