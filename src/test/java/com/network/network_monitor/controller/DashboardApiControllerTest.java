package com.network.network_monitor.controller;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.DashboardSummaryDto;
import com.network.network_monitor.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfig.class)
class DashboardApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    @WithMockUser(roles = "VIEWER")
    void getSummary_authenticated_returnsOkAndJson() throws Exception {
        DashboardSummaryDto dto = DashboardSummaryDto.builder()
                .totalDevices(5)
                .onlineDevices(4)
                .warningDevices(1)
                .offlineDevices(0)
                .totalOpenAlerts(2)
                .criticalAlerts(1)
                .warningAlerts(1)
                .infoAlerts(0)
                .averageLatencyMs(15.4)
                .reachabilityPercentage(98.5)
                .hasMetricData(true)
                .topLatencyDevices(List.of())
                .topAlertDevices(List.of())
                .recentAlerts(List.of())
                .build();

        when(dashboardService.getDashboardSummary()).thenReturn(dto);

        mvc.perform(get("/api/dashboard/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDevices").value(5))
                .andExpect(jsonPath("$.onlineDevices").value(4))
                .andExpect(jsonPath("$.totalOpenAlerts").value(2))
                .andExpect(jsonPath("$.criticalAlerts").value(1))
                .andExpect(jsonPath("$.hasMetricData").value(true))
                .andExpect(jsonPath("$.reachabilityPercentage").value(98.5));
    }

    @Test
    void getSummary_unauthenticated_isProtected() throws Exception {
        mvc.perform(get("/api/dashboard/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection());
    }
}
