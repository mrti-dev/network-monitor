package com.network.network_monitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.MaintenanceScheduleService;

@WebMvcTest(MaintenanceWebController.class)
@Import(SecurityConfig.class)
class MaintenanceSecurityPermissionTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MaintenanceScheduleService maintenanceService;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    void setUp() {
        DeviceFormDto dto = new DeviceFormDto();
        dto.setId(1L);
        dto.setName("Router Core");
        dto.setIpAddress("192.168.1.1");
        dto.setLocation("TP.HCM");
        when(deviceService.getDeviceFormById(1L)).thenReturn(dto);
        when(maintenanceService.getSchedulesByDeviceId(1L)).thenReturn(List.of());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_canViewMaintenancePage() throws Exception {
        mvc.perform(get("/devices/1/maintenance"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_forbidden_saveSchedule() throws Exception {
        mvc.perform(post("/devices/1/maintenance/save")
                .with(csrf())
                .param("title", "Nâng cấp router")
                .param("startsAt", "2026-10-06T14:00")
                .param("endsAt", "2026-10-06T15:00"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_forbidden_cancelSchedule() throws Exception {
        mvc.perform(post("/devices/1/maintenance/5/cancel")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_forbidden_saveSchedule() throws Exception {
        mvc.perform(post("/devices/1/maintenance/save")
                .with(csrf())
                .param("title", "Nâng cấp router"))
                .andExpect(status().is3xxRedirection()); // redirect to login
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canSaveSchedule() throws Exception {
        mvc.perform(post("/devices/1/maintenance/save")
                .with(csrf())
                .param("title", "Nâng cấp router")
                .param("startsAt", "2026-10-06T14:00")
                .param("endsAt", "2026-10-06T15:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/devices/1/maintenance"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canCancelSchedule() throws Exception {
        mvc.perform(post("/devices/1/maintenance/5/cancel")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/devices/1/maintenance"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void saveSchedule_requiresCsrf() throws Exception {
        mvc.perform(post("/devices/1/maintenance/save")
                .param("title", "Nâng cấp router"))
                .andExpect(status().isForbidden());
    }
}
