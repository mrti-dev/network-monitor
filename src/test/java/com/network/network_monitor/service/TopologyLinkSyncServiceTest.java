package com.network.network_monitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.network.network_monitor.dto.LldpNeighborDto;
import com.network.network_monitor.dto.TopologyDiscoveryResultDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.NetworkLink;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.LinkDiscoverySource;
import com.network.network_monitor.enums.LinkStatus;
import com.network.network_monitor.enums.LinkType;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.NetworkLinkRepository;

@ExtendWith(MockitoExtension.class)
class TopologyLinkSyncServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private NetworkLinkRepository networkLinkRepository;

    private TopologyLinkSyncService syncService;

    private Device devA;
    private Device devB;
    private Device devC;

    @BeforeEach
    void setUp() {
        syncService = new TopologyLinkSyncService(deviceRepository, networkLinkRepository);

        devA = Device.builder().id(1L).name("Core-SW-01").ipAddress("192.168.1.10")
                .macAddress("00:11:22:33:44:01").deviceType(DeviceType.SWITCH).status(DeviceStatus.ONLINE)
                .isMonitored(true).isDeleted(false).build();

        devB = Device.builder().id(2L).name("Dist-SW-01").ipAddress("192.168.1.20")
                .macAddress("00:11:22:33:44:02").deviceType(DeviceType.SWITCH).status(DeviceStatus.ONLINE)
                .isMonitored(true).isDeleted(false).build();

        devC = Device.builder().id(3L).name("Access-SW-01").ipAddress("192.168.1.30")
                .macAddress("00:11:22:33:44:03").deviceType(DeviceType.SWITCH).status(DeviceStatus.ONLINE)
                .isMonitored(true).isDeleted(false).build();
    }

    @Test
    void matchTargetDevice_byManagementIp() {
        LldpNeighborDto neighbor = LldpNeighborDto.builder()
                .remoteManagementIp("192.168.1.20")
                .build();

        Device matched = syncService.matchTargetDevice(neighbor, List.of(devA, devB, devC), devA);

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo(devB.getId());
    }

    @Test
    void matchTargetDevice_byMacAddress() {
        LldpNeighborDto neighbor = LldpNeighborDto.builder()
                .remoteChassisMac("00:11:22:33:44:02")
                .build();

        Device matched = syncService.matchTargetDevice(neighbor, List.of(devA, devB, devC), devA);

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo(devB.getId());
    }

    @Test
    void matchTargetDevice_bySysName() {
        LldpNeighborDto neighbor = LldpNeighborDto.builder()
                .remoteSysName("dist-sw-01.corp.local")
                .build();

        Device matched = syncService.matchTargetDevice(neighbor, List.of(devA, devB, devC), devA);

        assertThat(matched).isNotNull();
        assertThat(matched.getId()).isEqualTo(devB.getId());
    }

    @Test
    void matchTargetDevice_ambiguousIpOrMac_returnsNull() {
        Device duplicateDev = Device.builder().id(4L).name("Dupe-Dev").ipAddress("192.168.1.20")
                .macAddress("00:11:22:33:44:02").build();

        LldpNeighborDto neighbor = LldpNeighborDto.builder()
                .remoteManagementIp("192.168.1.20")
                .build();

        Device matched = syncService.matchTargetDevice(neighbor, List.of(devA, devB, duplicateDev), devA);

        assertThat(matched).isNull();
    }

    @Test
    void matchTargetDevice_selfLink_returnsNull() {
        LldpNeighborDto neighbor = LldpNeighborDto.builder()
                .remoteManagementIp("192.168.1.10") // Cùng IP với devA
                .build();

        Device matched = syncService.matchTargetDevice(neighbor, List.of(devA, devB), devA);

        assertThat(matched).isNull();
    }

    @Test
    void syncDiscoveredLinks_bidirectionalNeighbor_createsOnlyOneCanonicalLink() {
        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB));
        when(networkLinkRepository.findAll()).thenReturn(Collections.emptyList());
        when(networkLinkRepository.save(any(NetworkLink.class))).thenAnswer(i -> {
            NetworkLink l = i.getArgument(0);
            l.setId(100L);
            return l;
        });

        // devA thấy devB trên port Gi0/1 -> Gi0/2
        LldpNeighborDto aToB = LldpNeighborDto.builder()
                .localPort("Gi0/1").remotePortId("Gi0/2").remoteManagementIp("192.168.1.20").build();
        // devB thấy devA trên port Gi0/2 -> Gi0/1
        LldpNeighborDto bToA = LldpNeighborDto.builder()
                .localPort("Gi0/2").remotePortId("Gi0/1").remoteManagementIp("192.168.1.10").build();

        Map<Long, List<LldpNeighborDto>> map = Map.of(
                devA.getId(), List.of(aToB),
                devB.getId(), List.of(bToA)
        );

        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(map, 2, 0, 0, true);

        // Chỉ tạo 1 link duy nhất
        assertThat(result.getCreatedCount()).isEqualTo(1);
        ArgumentCaptor<NetworkLink> captor = ArgumentCaptor.forClass(NetworkLink.class);
        verify(networkLinkRepository).save(captor.capture());

        NetworkLink created = captor.getValue();
        // Canonical: devA (id 1) < devB (id 2)
        assertThat(created.getSourceDevice().getId()).isEqualTo(devA.getId());
        assertThat(created.getTargetDevice().getId()).isEqualTo(devB.getId());
        assertThat(created.getSourcePort()).isEqualTo("Gi0/1");
        assertThat(created.getTargetPort()).isEqualTo("Gi0/2");
        assertThat(created.getDiscoverySource()).isEqualTo(LinkDiscoverySource.LLDP);
        assertThat(created.getStatus()).isEqualTo(LinkStatus.UP);
    }

    @Test
    void syncDiscoveredLinks_updatesExistingLldpLink() {
        NetworkLink existing = NetworkLink.builder()
                .id(55L)
                .sourceDevice(devA)
                .targetDevice(devB)
                .sourcePort("Gi0/1")
                .targetPort("Gi0/2")
                .status(LinkStatus.DOWN)
                .discoverySource(LinkDiscoverySource.LLDP)
                .build();

        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB));
        when(networkLinkRepository.findAll()).thenReturn(List.of(existing));
        when(networkLinkRepository.save(any(NetworkLink.class))).thenAnswer(i -> i.getArgument(0));

        LldpNeighborDto aToB = LldpNeighborDto.builder()
                .localPort("Gi0/1").remotePortId("Gi0/2").remoteManagementIp("192.168.1.20").build();

        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(
                Map.of(devA.getId(), List.of(aToB)), 1, 0, 0, true);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getCreatedCount()).isEqualTo(0);
        assertThat(existing.getStatus()).isEqualTo(LinkStatus.UP);
        assertThat(existing.getSourcePort()).isEqualTo("Gi0/1");
        assertThat(existing.getTargetPort()).isEqualTo("Gi0/2");
        assertThat(existing.getLastDiscoveredAt()).isNotNull();
    }

    @Test
    void syncDiscoveredLinks_doesNotModifyManualLink() {
        NetworkLink manualLink = NetworkLink.builder()
                .id(77L)
                .sourceDevice(devA)
                .targetDevice(devB)
                .sourcePort("Gi0/1")
                .targetPort("Gi0/2")
                .status(LinkStatus.UP)
                .discoverySource(LinkDiscoverySource.MANUAL)
                .build();

        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB));
        when(networkLinkRepository.findAll()).thenReturn(List.of(manualLink));

        LldpNeighborDto aToB = LldpNeighborDto.builder()
                .localPort("Gi0/1").remotePortId("Gi0/2").remoteManagementIp("192.168.1.20").build();

        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(
                Map.of(devA.getId(), List.of(aToB)), 1, 0, 0, true);

        assertThat(result.getCreatedCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(manualLink.getSourcePort()).isEqualTo("Gi0/1");
        verify(networkLinkRepository, never()).save(any());
    }

    @Test
    void syncDiscoveredLinks_marksOldLldpLinkAsStale_whenDiscoveryIsValid() {
        NetworkLink activeDiscovered = NetworkLink.builder()
                .id(101L).sourceDevice(devA).targetDevice(devB)
                .sourcePort("Gi0/1").targetPort("Gi0/2")
                .discoverySource(LinkDiscoverySource.LLDP).status(LinkStatus.UP).build();

        NetworkLink oldMissing = NetworkLink.builder()
                .id(102L).sourceDevice(devB).targetDevice(devC)
                .discoverySource(LinkDiscoverySource.LLDP).status(LinkStatus.UP).build();

        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB, devC));
        when(networkLinkRepository.findAll()).thenReturn(List.of(activeDiscovered, oldMissing));
        when(networkLinkRepository.findByDiscoverySource(LinkDiscoverySource.LLDP))
                .thenReturn(List.of(activeDiscovered, oldMissing));
        when(networkLinkRepository.save(any(NetworkLink.class))).thenAnswer(i -> i.getArgument(0));

        // Chỉ tìm thấy A <-> B, không còn B <-> C
        LldpNeighborDto aToB = LldpNeighborDto.builder()
                .localPort("Gi0/1").remotePortId("Gi0/2").remoteManagementIp("192.168.1.20").build();

        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(
                Map.of(devA.getId(), List.of(aToB), devB.getId(), List.of()), 2, 0, 0, true);

        assertThat(oldMissing.getStatus()).isEqualTo(LinkStatus.STALE);
    }

    @Test
    void syncDiscoveredLinks_keepsParallelLinksBetweenSameDevices() {
        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB));
        when(networkLinkRepository.findAll()).thenReturn(List.of());
        when(networkLinkRepository.save(any(NetworkLink.class))).thenAnswer(i -> {
            NetworkLink link = i.getArgument(0);
            link.setId("Gi0/1".equals(link.getSourcePort()) ? 101L : 102L);
            return link;
        });

        List<LldpNeighborDto> neighbors = List.of(
                LldpNeighborDto.builder().localPort("Gi0/1").remotePortId("Gi0/2")
                        .remoteManagementIp(devB.getIpAddress()).build(),
                LldpNeighborDto.builder().localPort("Gi0/3").remotePortId("Gi0/4")
                        .remoteManagementIp(devB.getIpAddress()).build());

        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(
                Map.of(devA.getId(), neighbors), 1, 0, 0, true);

        assertThat(result.getCreatedCount()).isEqualTo(2);
        ArgumentCaptor<NetworkLink> captor = ArgumentCaptor.forClass(NetworkLink.class);
        org.mockito.Mockito.verify(networkLinkRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(NetworkLink::getSourcePort)
                .containsExactlyInAnyOrder("Gi0/1", "Gi0/3");
    }

    @Test
    void syncDiscoveredLinks_doesNotMarkUnscannedLinkStale() {
        NetworkLink unscanned = NetworkLink.builder().id(102L).sourceDevice(devB).targetDevice(devC)
                .sourcePort("Gi0/1").targetPort("Gi0/2")
                .discoverySource(LinkDiscoverySource.LLDP).status(LinkStatus.UP).build();
        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB, devC));
        when(networkLinkRepository.findAll()).thenReturn(List.of(unscanned));
        when(networkLinkRepository.findByDiscoverySource(LinkDiscoverySource.LLDP)).thenReturn(List.of(unscanned));

        syncService.syncDiscoveredLinks(Map.of(devA.getId(), List.of()), 3, 2, 0, true);

        assertThat(unscanned.getStatus()).isEqualTo(LinkStatus.UP);
        verify(networkLinkRepository, never()).save(any());
    }

    @Test
    void syncDiscoveredLinks_doesNotMarkStale_whenDiscoveryInvalidOrAllFailed() {
        NetworkLink oldLink = NetworkLink.builder()
                .id(102L).sourceDevice(devB).targetDevice(devC)
                .discoverySource(LinkDiscoverySource.LLDP).status(LinkStatus.UP).build();

        when(deviceRepository.findAll()).thenReturn(List.of(devA, devB, devC));
        when(networkLinkRepository.findAll()).thenReturn(List.of(oldLink));

        // hasValidDiscovery = false (tất cả thiết bị timeout / không hỗ trợ)
        TopologyDiscoveryResultDto result = syncService.syncDiscoveredLinks(
                Collections.emptyMap(), 2, 2, 0, false);

        assertThat(result.getCreatedCount()).isEqualTo(0);
        assertThat(result.getUpdatedCount()).isEqualTo(0);
        assertThat(oldLink.getStatus()).isEqualTo(LinkStatus.UP);
        verify(networkLinkRepository, never()).save(any());
    }
}
