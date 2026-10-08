package com.network.network_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.network.network_monitor.dto.NetworkLinkDto;
import com.network.network_monitor.dto.NetworkLinkFormDto;
import com.network.network_monitor.dto.TopologyDataDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.NetworkLink;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.LinkDiscoverySource;
import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.repository.NetworkLinkRepository;
import com.network.network_monitor.repository.SnmpMetricLogRepository;
import com.network.network_monitor.service.impl.TopologyServiceImpl;

@ExtendWith(MockitoExtension.class)
class TopologyServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private NetworkLinkRepository networkLinkRepository;
    @Mock
    private MetricLogRepository metricLogRepository;
    @Mock
    private SnmpMetricLogRepository snmpMetricLogRepository;
    @Mock
    private com.network.network_monitor.repository.SnmpConfigRepository snmpConfigRepository;
    @Mock
    private com.network.network_monitor.strategy.SnmpProbingStrategy snmpProbingStrategy;
    @Mock
    private TopologyLinkSyncService topologyLinkSyncService;

    private TopologyServiceImpl topologyService;

    private Device router;
    private Device sw;
    private Device server;

    @BeforeEach
    void setUp() {
        topologyService = new TopologyServiceImpl(
                deviceRepository,
                networkLinkRepository,
                metricLogRepository,
                snmpMetricLogRepository,
                snmpConfigRepository,
                snmpProbingStrategy,
                topologyLinkSyncService
        );

        router = Device.builder().id(1L).name("Core-Router").ipAddress("192.168.1.1")
                .deviceType(DeviceType.ROUTER).status(DeviceStatus.ONLINE).isMonitored(true).isDeleted(false).build();
        sw = Device.builder().id(2L).name("Dist-Switch").ipAddress("192.168.1.2")
                .deviceType(DeviceType.SWITCH).status(DeviceStatus.ONLINE).isMonitored(true).isDeleted(false).build();
        server = Device.builder().id(3L).name("App-Server").ipAddress("192.168.1.10")
                .deviceType(DeviceType.SERVER).status(DeviceStatus.OFFLINE).isMonitored(true).isDeleted(false).build();
    }

    @Test
    void getTopologyData_evaluatesDynamicLinkStatusCorrectly() {
        NetworkLink link1 = NetworkLink.builder().id(101L).sourceDevice(router).targetDevice(sw)
                .linkType(LinkType.FIBER).status(LinkStatus.UP).bandwidthMbps(10000)
                .discoverySource(LinkDiscoverySource.MANUAL).build();
        NetworkLink link2 = NetworkLink.builder().id(102L).sourceDevice(sw).targetDevice(server)
                .linkType(LinkType.ETHERNET).status(LinkStatus.UP).bandwidthMbps(1000)
                .discoverySource(LinkDiscoverySource.MANUAL).build();

        when(deviceRepository.findAll()).thenReturn(List.of(router, sw, server));
        when(networkLinkRepository.findActiveLinks()).thenReturn(List.of(link1, link2));
        when(metricLogRepository.findTop30ByDeviceIdOrderByRecordedAtDescIdDesc(any())).thenReturn(Collections.emptyList());
        when(snmpMetricLogRepository.findLatestByDeviceId(any(), any())).thenReturn(Collections.emptyList());

        TopologyDataDto data = topologyService.getTopologyData();

        assertThat(data.getTotalNodes()).isEqualTo(3);
        assertThat(data.getOnlineNodes()).isEqualTo(2);
        assertThat(data.getOfflineNodes()).isEqualTo(1);
        assertThat(data.getTotalLinks()).isEqualTo(2);

        // Link1 between Online Router and Online Switch => UP
        assertThat(data.getLinks().get(0).getStatus()).isEqualTo(LinkStatus.UP);
        // Link2 between Online Switch and OFFLINE Server => DOWN
        assertThat(data.getLinks().get(1).getStatus()).isEqualTo(LinkStatus.DOWN);
        assertThat(data.getDownLinks()).isEqualTo(1);
    }

    @Test
    void createLink_success_manualSourceAssigned() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(router));
        when(deviceRepository.findById(2L)).thenReturn(Optional.of(sw));
        when(networkLinkRepository.findAllLinksBetweenDevices(1L, 2L)).thenReturn(Collections.emptyList());
        when(networkLinkRepository.save(any(NetworkLink.class))).thenAnswer(i -> {
            NetworkLink l = i.getArgument(0);
            l.setId(10L);
            return l;
        });

        NetworkLinkFormDto form = NetworkLinkFormDto.builder()
                .sourceDeviceId(1L)
                .targetDeviceId(2L)
                .sourcePort("eth0")
                .targetPort("gi0/1")
                .linkType(LinkType.FIBER)
                .bandwidthMbps(10000)
                .build();

        NetworkLinkDto dto = topologyService.createLink(form);

        assertThat(dto).isNotNull();
        assertThat(dto.getSourceDeviceId()).isEqualTo(1L);
        assertThat(dto.getTargetDeviceId()).isEqualTo(2L);
        assertThat(dto.getDiscoverySource()).isEqualTo(LinkDiscoverySource.MANUAL);
        verify(networkLinkRepository).save(any(NetworkLink.class));
    }

    @Test
    void createLink_sameDevice_throwsException() {
        NetworkLinkFormDto form = NetworkLinkFormDto.builder()
                .sourceDeviceId(1L)
                .targetDeviceId(1L)
                .build();

        assertThatThrownBy(() -> topologyService.createLink(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được trùng nhau");
    }

    @Test
    void createLink_duplicateReverseDirection_throwsException() {
        // Link đã tồn tại: 1 -> 2 với port eth0 -> gi0/1
        NetworkLink existing = NetworkLink.builder()
                .id(99L)
                .sourceDevice(router)
                .targetDevice(sw)
                .sourcePort("eth0")
                .targetPort("gi0/1")
                .build();

        when(deviceRepository.findById(2L)).thenReturn(Optional.of(sw));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(router));
        when(networkLinkRepository.findAllLinksBetweenDevices(2L, 1L)).thenReturn(List.of(existing));

        // Thử tạo link ngược chiều: 2 -> 1 với gi0/1 -> eth0
        NetworkLinkFormDto reverseForm = NetworkLinkFormDto.builder()
                .sourceDeviceId(2L)
                .targetDeviceId(1L)
                .sourcePort("gi0/1")
                .targetPort("eth0")
                .build();

        assertThatThrownBy(() -> topologyService.createLink(reverseForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    void autoDiscoverLinks_noEvidence_doesNotCreateFakeLinks() {
        when(snmpConfigRepository.findActiveConfigs()).thenReturn(Collections.emptyList());
        when(topologyLinkSyncService.syncDiscoveredLinks(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(com.network.network_monitor.dto.TopologyDiscoveryResultDto.builder().createdCount(0).build());

        int created = topologyService.autoDiscoverLinks();

        // Không tạo bất kỳ link nào dựa trên subnet/group/type
        assertThat(created).isEqualTo(0);
        verify(networkLinkRepository, never()).save(any(NetworkLink.class));
    }

    @Test
    void autoDiscoverLinks_withoutDiscoverySupport_doesNotModifyExistingLinks() {
        when(snmpConfigRepository.findActiveConfigs()).thenReturn(Collections.emptyList());
        when(topologyLinkSyncService.syncDiscoveredLinks(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(com.network.network_monitor.dto.TopologyDiscoveryResultDto.builder().createdCount(0).build());

        NetworkLink oldLldp = NetworkLink.builder()
                .id(50L)
                .sourceDevice(router)
                .targetDevice(sw)
                .discoverySource(LinkDiscoverySource.LLDP)
                .status(LinkStatus.UP)
                .build();

        int created = topologyService.autoDiscoverLinks();

        assertThat(created).isEqualTo(0);
        assertThat(oldLldp.getStatus()).isEqualTo(LinkStatus.UP);
        verify(networkLinkRepository, never()).save(any(NetworkLink.class));
        verify(networkLinkRepository, never()).delete(any());
        verify(networkLinkRepository, never()).deleteById(any());
    }
}
