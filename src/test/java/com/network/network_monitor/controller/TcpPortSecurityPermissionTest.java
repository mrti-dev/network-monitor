package com.network.network_monitor.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.TcpPortMonitorService;

@WebMvcTest(TcpPortWebController.class)
@Import(SecurityConfig.class)
class TcpPortSecurityPermissionTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TcpPortMonitorService portService;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private AppUserDetailsService appUserDetailsService;

    @BeforeEach
    void setUp() {
        DeviceFormDto dto = new DeviceFormDto();
        dto.setId(1L);
        dto.setName("Switch HN");
        dto.setIpAddress("192.168.1.1");
        dto.setLocation("Hà Nội");
        dto.setDeviceType(com.network.network_monitor.enums.DeviceType.SWITCH);
        when(deviceService.getDeviceFormById(1L)).thenReturn(dto);
        when(portService.getPortsByDeviceId(1L)).thenReturn(List.of());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_canViewDevicePorts() throws Exception {
        mvc.perform(get("/devices/1/ports"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_forbidden_savePort() throws Exception {
        mvc.perform(post("/devices/1/ports/save")
                .with(csrf())
                .param("port", "80")
                .param("serviceName", "HTTP"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_forbidden_togglePort() throws Exception {
        mvc.perform(post("/devices/1/ports/10/toggle")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewer_forbidden_deletePort() throws Exception {
        mvc.perform(post("/devices/1/ports/10/delete")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canSavePort() throws Exception {
        mvc.perform(post("/devices/1/ports/save")
                .with(csrf())
                .param("port", "80")
                .param("serviceName", "HTTP")
                .param("timeoutMs", "2000")
                .param("failureThreshold", "3")
                .param("successThreshold", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/devices/1/ports"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_canTogglePort() throws Exception {
        mvc.perform(post("/devices/1/ports/10/toggle")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/devices/1/ports"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operator_forbidden_deletePort() throws Exception {
        mvc.perform(post("/devices/1/ports/10/delete")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canDeletePort() throws Exception {
        mvc.perform(post("/devices/1/ports/10/delete")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/devices/1/ports"));
    }
}
