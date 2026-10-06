package com.network.network_monitor.controller;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.*;
import com.network.network_monitor.service.AlertService;
import com.network.network_monitor.service.DashboardService;
import com.network.network_monitor.service.DeviceGroupService;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.DiscoveryService;
import com.network.network_monitor.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Kiểm thử phân quyền theo ma trận ADMIN/OPERATOR/VIEWER.
 * Mỗi test case mô tả đúng 1 tình huống phân quyền cụ thể.
 */
@WebMvcTest({DeviceWebController.class, AlertWebController.class, DiscoveryController.class, AuthController.class, OperationsWebController.class})
@Import(SecurityConfig.class)
class SecurityPermissionTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean DeviceService deviceService;
    @MockitoBean AlertService alertService;
    @MockitoBean DeviceGroupService deviceGroupService;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean DiscoveryService discoveryService;
    @MockitoBean UserService userService;
    @MockitoBean AppUserDetailsService appUserDetailsService;
    @MockitoBean com.network.network_monitor.repository.NotificationLogRepository notificationLogRepository;

    @BeforeEach
    void setUp() {
        when(dashboardService.getDashboardSummary()).thenReturn(DashboardSummaryDto.builder()
                .totalDevices(0).onlineDevices(0).warningDevices(0).offlineDevices(0).unknownDevices(0).maintenanceDevices(0)
                .monitoredDevices(0).totalOpenAlerts(0).triggeredAlerts(0).acknowledgedAlerts(0).resolvedAlerts(0)
                .criticalAlerts(0).warningAlerts(0).infoAlerts(0).averageLatencyMs(0.0).reachabilityPercentage(100.0)
                .topLatencyDevices(java.util.List.of()).topAlertDevices(java.util.List.of()).recentAlerts(java.util.List.of())
                .build());
        when(deviceGroupService.getAllGroups()).thenReturn(java.util.List.of());
    }

    // ------------------------------------------------------------------
    // Unauthenticated → redirect to login
    // ------------------------------------------------------------------

    @Test
    void unauthenticated_deviceList_redirectsToLogin() throws Exception {
        mvc.perform(get("/devices"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unauthenticated_register_isAllowed() throws Exception {
        mvc.perform(get("/register"))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_scanApi_redirectsOrUnauthorized() throws Exception {
        var result = mvc.perform(post("/api/discovery/scan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subnet\":\"192.168.1.0/24\"}"))
            .andReturn();
        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
            .as("Unauthenticated request should be rejected (3xx or 4xx)")
            .isIn(302, 401, 403);
    }

    // ------------------------------------------------------------------
    // VIEWER — chỉ đọc
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_canViewDeviceList() throws Exception {
        when(deviceService.searchDevices(any(), any())).thenReturn(Page.empty());
        mvc.perform(get("/devices"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotDelete_forbidden() throws Exception {
        mvc.perform(post("/devices/delete/1").with(csrf()))
            .andExpect(status().isForbidden());
        verify(deviceService, never()).deleteDevice(any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotToggle_forbidden() throws Exception {
        mvc.perform(post("/devices/toggle/1").with(csrf()))
            .andExpect(status().isForbidden());
        verify(deviceService, never()).toggleMonitoring(any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotScan_forbidden() throws Exception {
        mvc.perform(post("/api/discovery/scan")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subnet\":\"192.168.1.0/24\"}"))
            .andExpect(status().isForbidden());
        verify(discoveryService, never()).scanNetwork(any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotAcknowledgeAlert_forbidden() throws Exception {
        mvc.perform(post("/alerts/1/acknowledge").with(csrf()))
            .andExpect(status().isForbidden());
        verify(alertService, never()).acknowledgeAlert(any(), any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotResolveAlert_forbidden() throws Exception {
        mvc.perform(post("/alerts/1/resolve").with(csrf()))
            .andExpect(status().isForbidden());
        verify(alertService, never()).resolveAlert(any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_cannotToggleMaintenance_forbidden() throws Exception {
        mvc.perform(post("/devices/maintenance/1").with(csrf()))
            .andExpect(status().isForbidden());
        verify(deviceService, never()).toggleMaintenanceMode(any());
    }

    // ------------------------------------------------------------------
    // OPERATOR — điều hành: scan + toggle + alert ack/resolve, không xóa
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canToggleMonitoring() throws Exception {
        mvc.perform(post("/devices/toggle/1").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(deviceService).toggleMonitoring(1L);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canScan_withValidCsrf() throws Exception {
        ScanResponseDto resp = ScanResponseDto.builder()
                .discovered(0).added(0).details(java.util.List.of()).build();
        when(discoveryService.scanNetwork(any())).thenReturn(resp);
        mvc.perform(post("/api/discovery/scan")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subnet\":\"192.168.1.0/24\"}"))
            .andExpect(status().isOk());
        verify(discoveryService).scanNetwork(any());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canAcknowledgeAlert() throws Exception {
        mvc.perform(post("/alerts/1/acknowledge").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(alertService).acknowledgeAlert(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canResolveAlert() throws Exception {
        mvc.perform(post("/alerts/1/resolve").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(alertService).resolveAlert(eq(1L));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canToggleMaintenance() throws Exception {
        mvc.perform(post("/devices/maintenance/1").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(deviceService).toggleMaintenanceMode(1L);
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_cannotDeleteDevice_forbidden() throws Exception {
        mvc.perform(post("/devices/delete/1").with(csrf()))
            .andExpect(status().isForbidden());
        verify(deviceService, never()).deleteDevice(any());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_cannotDeleteGroup_forbidden() throws Exception {
        mvc.perform(post("/monitoring/groups/delete/1").with(csrf()))
            .andExpect(status().isForbidden());
        verify(deviceGroupService, never()).deleteGroup(any());
    }

    // ------------------------------------------------------------------
    // ADMIN — toàn quyền
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDeleteDevice_withCsrf() throws Exception {
        mvc.perform(post("/devices/delete/1").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(deviceService).deleteDevice(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDeleteGroup_withCsrf() throws Exception {
        mvc.perform(post("/monitoring/groups/delete/1").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(deviceGroupService).deleteGroup(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canAcknowledgeAlert_withCsrf() throws Exception {
        mvc.perform(post("/alerts/1/acknowledge").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(alertService).acknowledgeAlert(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canResolveAlert_withCsrf() throws Exception {
        mvc.perform(post("/alerts/1/resolve").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verify(alertService).resolveAlert(eq(1L));
    }

    // ------------------------------------------------------------------
    // CSRF protection
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_delete_withoutCsrf_forbidden() throws Exception {
        mvc.perform(post("/devices/delete/1"))
            .andExpect(status().isForbidden());
        verify(deviceService, never()).deleteDevice(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_acknowledge_withoutCsrf_forbidden() throws Exception {
        mvc.perform(post("/alerts/1/acknowledge"))
            .andExpect(status().isForbidden());
        verify(alertService, never()).acknowledgeAlert(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_resolve_withoutCsrf_forbidden() throws Exception {
        mvc.perform(post("/alerts/1/resolve"))
            .andExpect(status().isForbidden());
        verify(alertService, never()).resolveAlert(any());
    }

    // ------------------------------------------------------------------
    // Error response không lộ stack trace / tên class nội bộ
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "ADMIN")
    void errorResponse_doesNotExposeInternalException() throws Exception {
        when(discoveryService.scanNetwork(any()))
            .thenThrow(new RuntimeException("internal db connection pool exhausted at line 247"));
        mvc.perform(post("/api/discovery/scan")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subnet\":\"192.168.1.0/24\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Không thể hoàn tất thao tác. Vui lòng thử lại sau."))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("internal db connection pool"))));
    }

    // ------------------------------------------------------------------
    // Login page accessible without auth
    // ------------------------------------------------------------------

    @Test
    void loginPage_accessible_withoutAuth() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().isOk());
    }
}
