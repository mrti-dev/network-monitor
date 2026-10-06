package com.network.network_monitor.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceMetricApiController {

    private final MetricLogRepository metricLogRepository;
    private final DeviceRepository deviceRepository;

    private static final Pattern RANGE_PATTERN = Pattern.compile("^([1-9][0-9]*)([mhd])$");

    @GetMapping("/{id}/metrics")
    public ResponseEntity<List<MetricLogDto>> getDeviceMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1h") String range,
            @RequestParam(defaultValue = "100") Integer limit) {

        if (!deviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + id);
        }

        if (limit == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Tham số limit phải trong khoảng từ 1 đến 500");
        }

        LocalDateTime since = parseRangeToDateTime(range);

        List<MetricLogDto> metrics = new ArrayList<>(metricLogRepository.findLatestMetricsByDeviceIdAndSince(
                id, since, PageRequest.of(0, limit)));
        Collections.reverse(metrics);

        return ResponseEntity.ok(metrics);
    }

    private LocalDateTime parseRangeToDateTime(String range) {
        if (range == null || range.isBlank()) {
            return LocalDateTime.now().minusHours(1);
        }

        Matcher matcher = RANGE_PATTERN.matcher(range.trim().toLowerCase());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Tham số range không hợp lệ. Ví dụ hợp lệ: 15m, 30m, 1h, 6h, 24h, 7d");
        }

        int amount = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        LocalDateTime now = LocalDateTime.now();
        return switch (unit) {
            case "m" -> now.minusMinutes(amount);
            case "h" -> now.minusHours(amount);
            case "d" -> now.minusDays(amount);
            default -> throw new IllegalArgumentException("Đơn vị thời gian không được hỗ trợ: " + unit);
        };
    }
}
