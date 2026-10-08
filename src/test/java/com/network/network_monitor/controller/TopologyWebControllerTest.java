package com.network.network_monitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.network.network_monitor.dto.NetworkLinkDto;
import com.network.network_monitor.dto.NetworkLinkFormDto;
import com.network.network_monitor.dto.TopologyDataDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.TopologyService;

@ExtendWith(MockitoExtension.class)
class TopologyWebControllerTest {

    @Mock
    private TopologyService topologyService;
    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private TopologyWebController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void viewTopology_returnsViewAndModel() throws Exception {
        TopologyDataDto data = TopologyDataDto.builder()
                .nodes(Collections.emptyList())
                .links(Collections.emptyList())
                .totalNodes(0)
                .totalLinks(0)
                .build();

        when(topologyService.getTopologyData()).thenReturn(data);
        when(deviceService.getAllDevices(any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/topology"))
                .andExpect(status().isOk())
                .andExpect(view().name("operations/topology"))
                .andExpect(model().attributeExists("topology"))
                .andExpect(model().attributeExists("devices"))
                .andExpect(model().attributeExists("linkForm"));
    }

    @Test
    void getTopologyApi_returnsJson() throws Exception {
        TopologyDataDto data = TopologyDataDto.builder()
                .nodes(Collections.emptyList())
                .links(Collections.emptyList())
                .totalNodes(5)
                .totalLinks(3)
                .build();

        when(topologyService.getTopologyData()).thenReturn(data);

        mockMvc.perform(get("/api/topology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNodes").value(5))
                .andExpect(jsonPath("$.totalLinks").value(3));
    }

    @Test
    void createLinkApi_success() throws Exception {
        NetworkLinkDto dto = NetworkLinkDto.builder().id(100L).sourceDeviceId(1L).targetDeviceId(2L).build();
        when(topologyService.createLink(any(NetworkLinkFormDto.class))).thenReturn(dto);

        mockMvc.perform(post("/api/topology/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceDeviceId\": 1, \"targetDeviceId\": 2, \"linkType\": \"ETHERNET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    void deleteLinkApi_success() throws Exception {
        mockMvc.perform(delete("/api/topology/links/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(topologyService).deleteLink(100L);
    }

    @Test
    void autoDiscoverApi_success() throws Exception {
        com.network.network_monitor.dto.TopologyDiscoveryResultDto res = com.network.network_monitor.dto.TopologyDiscoveryResultDto.builder()
                .createdCount(4)
                .updatedCount(2)
                .scannedDevices(5)
                .failedDevices(1)
                .build();
        when(topologyService.autoDiscoverLinksDetailed()).thenReturn(res);

        mockMvc.perform(post("/api/topology/auto-discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.scanned").value(5))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Đã phát hiện 4 liên kết mới, cập nhật 2 liên kết")));
    }

    @Test
    void autoDiscoverApi_noEvidence_returnsZeroAndMessage() throws Exception {
        com.network.network_monitor.dto.TopologyDiscoveryResultDto res = com.network.network_monitor.dto.TopologyDiscoveryResultDto.builder()
                .createdCount(0)
                .updatedCount(0)
                .scannedDevices(3)
                .failedDevices(0)
                .build();
        when(topologyService.autoDiscoverLinksDetailed()).thenReturn(res);

        mockMvc.perform(post("/api/topology/auto-discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không tìm thấy liên kết LLDP mới")));
    }

    @Test
    void autoDiscoverForm_redirectsAndSetsFlashMessage() throws Exception {
        com.network.network_monitor.dto.TopologyDiscoveryResultDto res = com.network.network_monitor.dto.TopologyDiscoveryResultDto.builder()
                .createdCount(2)
                .updatedCount(1)
                .scannedDevices(3)
                .failedDevices(0)
                .build();
        when(topologyService.autoDiscoverLinksDetailed()).thenReturn(res);

        mockMvc.perform(post("/topology/auto-discover"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/topology"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("successMessage"));
    }
}
