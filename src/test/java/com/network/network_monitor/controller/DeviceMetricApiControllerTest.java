package com.network.network_monitor.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.MetricLogDto;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;

@WebMvcTest(DeviceMetricApiController.class)
@Import({SecurityConfig.class, com.network.network_monitor.exception.GlobalExceptionHandler.class})
class DeviceMetricApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MetricLogRepository metricLogRepository;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    void setUp() {
        when(deviceRepository.existsById(1L)).thenReturn(true);
        when(deviceRepository.existsById(999L)).thenReturn(false);
    }

    @Test
    void unauthenticated_metricsApi_isRejected() throws Exception {
        mvc.perform(get("/api/devices/1/metrics"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(s).isIn(302, 401, 403);
                });
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_canAccessMetricsApi_withDefaultRangeAndLimit() throws Exception {
        MetricLogDto dto = new MetricLogDto(LocalDateTime.now(), 15.2, 0.0, true);
        when(metricLogRepository.findLatestMetricsByDeviceIdAndSince(eq(1L), any(), any()))
                .thenReturn(List.of(dto));

        mvc.perform(get("/api/devices/1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latencyMs").value(15.2))
                .andExpect(jsonPath("$[0].isReachable").value(true));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_validRangeAndLimit_returnsOk() throws Exception {
        when(metricLogRepository.findLatestMetricsByDeviceIdAndSince(eq(1L), any(), any()))
                .thenReturn(List.of());

        mvc.perform(get("/api/devices/1/metrics?range=30m&limit=50"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void metricsApi_returnsLatestSamplesInChronologicalOrder() throws Exception {
        LocalDateTime newest = LocalDateTime.of(2026, 10, 6, 12, 2);
        LocalDateTime older = newest.minusMinutes(1);
        when(metricLogRepository.findLatestMetricsByDeviceIdAndSince(eq(1L), any(), any()))
                .thenReturn(List.of(
                        new MetricLogDto(newest, 20.0, 0.0, true),
                        new MetricLogDto(older, 10.0, 0.0, true)));

        mvc.perform(get("/api/devices/1/metrics?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latencyMs").value(10.0))
                .andExpect(jsonPath("$[1].latencyMs").value(20.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_invalidRange_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/devices/1/metrics?range=invalid_range"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_invalidLimit_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/devices/1/metrics?limit=0"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/devices/1/metrics?limit=1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_deviceNotFound_returnsNotFound() throws Exception {
        mvc.perform(get("/api/devices/999/metrics"))
                .andExpect(status().isNotFound());
    }
}
