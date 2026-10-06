package com.network.network_monitor.service.impl;

import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.DeviceFilterDto;
import com.network.network_monitor.dto.DeviceFormDto;
import com.network.network_monitor.dto.DeviceGroupDto;
import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.DiscoveryOutcome;
import com.network.network_monitor.dto.MonitoringUpdateDto;
import com.network.network_monitor.dto.ScanResponseDto.DiscoveredDevice;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceGroup;
import com.network.network_monitor.entity.MonitoringConfig;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceGroupRepository;
import com.network.network_monitor.repository.DeviceLogRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.repository.MetricLogRepository;
import com.network.network_monitor.scheduler.HealthEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceGroupAndFilterTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceGroupRepository groupRepository;
    @Mock private HealthEvaluator healthEvaluator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MetricLogRepository metricLogRepository;
    @Mock private DeviceLogRepository deviceLogRepository;

    private MonitoringDefaults monitoringDefaults;
    private DeviceServiceImpl deviceService;
    private DeviceGroupServiceImpl groupService;
    private DiscoveryTransactionService discoveryTransactionService;

    @BeforeEach
    void setUp() {
        monitoringDefaults = new MonitoringDefaults();
        monitoringDefaults.setPingInterval(30);
        monitoringDefaults.setTimeoutMs(2000);
        monitoringDefaults.setLatencyThreshold(50.0);

        deviceService = new DeviceServiceImpl(deviceRepository, groupRepository, healthEvaluator, monitoringDefaults, eventPublisher, metricLogRepository, deviceLogRepository);
        groupService = new DeviceGroupServiceImpl(groupRepository, deviceRepository);
        discoveryTransactionService = new DiscoveryTransactionService(deviceRepository, deviceLogRepository, healthEvaluator);
    }

    @Test
    void createOrUpdateGroup_success() {
        DeviceGroupDto dto = DeviceGroupDto.builder()
                .name("Core Infrastructure")
                .description("All core switches and routers")
                .pingInterval(30)
                .timeoutMs(2000)
                .latencyThreshold(50.0)
                .build();

        when(groupRepository.save(any(DeviceGroup.class))).thenAnswer(inv -> {
            DeviceGroup g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        DeviceGroupDto saved = groupService.saveGroup(dto);
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("Core Infrastructure");
    }

    @Test
    void deleteGroup_unlinksDevicesFromGroup() {
        DeviceGroup group = DeviceGroup.builder().id(5L).name("Branch Group").devices(new ArrayList<>()).build();
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));

        groupService.deleteGroup(5L);

        verify(deviceRepository).unlinkDevicesFromGroup(5L);
        verify(groupRepository).delete(group);
    }

    @Test
    void updateMonitoringConfig_withUseGroupConfig_setsOverridesToNull() {
        DeviceGroup group = DeviceGroup.builder()
                .id(1L)
                .name("Default Group")
                .pingInterval(45)
                .timeoutMs(2500)
                .latencyThreshold(80.0)
                .build();

        MonitoringConfig config = MonitoringConfig.builder()
                .pingInterval(10)
                .timeoutMs(1000)
                .latencyThreshold(40.0)
                .build();

        Device device = Device.builder()
                .id(1L)
                .name("Test Device")
                .group(group)
                .monitoringConfig(config)
                .build();
        config.setDevice(device);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        MonitoringUpdateDto updateDto = new MonitoringUpdateDto();
        updateDto.setDeviceId(1L);
        updateDto.setUseGroupConfig(true);

        deviceService.updateMonitoringConfig(updateDto);

        assertThat(device.getMonitoringConfig().getPingInterval()).isNull();
        assertThat(device.getMonitoringConfig().getTimeoutMs()).isNull();
        assertThat(device.getMonitoringConfig().getLatencyThreshold()).isNull();
    }

    @Test
    void effectiveConfig_deviceNullOverride_inheritsGroupConfig() {
        DeviceGroup group = DeviceGroup.builder()
                .id(1L)
                .name("Group A")
                .pingInterval(40)
                .timeoutMs(3000)
                .latencyThreshold(65.0)
                .build();

        MonitoringConfig config = MonitoringConfig.builder()
                .pingInterval(null)
                .timeoutMs(null)
                .latencyThreshold(null)
                .build();

        Device device = Device.builder()
                .id(1L)
                .name("Device A")
                .ipAddress("192.168.1.10")
                .group(group)
                .monitoringConfig(config)
                .build();
        config.setDevice(device);

        when(deviceRepository.findFilteredDevices(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(device)));

        Page<DeviceResponseDto> result = deviceService.searchDevices(new DeviceFilterDto(), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        DeviceResponseDto dto = result.getContent().get(0);

        assertThat(dto.getPingInterval()).isEqualTo(40);
        assertThat(dto.getTimeoutMs()).isEqualTo(3000);
        assertThat(dto.getLatencyThreshold()).isEqualTo(65.0);
    }

    @Test
    void effectiveConfig_groupNull_inheritsAppDefaults() {
        MonitoringConfig config = MonitoringConfig.builder()
                .pingInterval(null)
                .timeoutMs(null)
                .latencyThreshold(null)
                .build();

        Device device = Device.builder()
                .id(2L)
                .name("Device B")
                .ipAddress("192.168.1.20")
                .group(null)
                .monitoringConfig(config)
                .build();
        config.setDevice(device);

        when(deviceRepository.findFilteredDevices(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(device)));

        Page<DeviceResponseDto> result = deviceService.searchDevices(new DeviceFilterDto(), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        DeviceResponseDto dto = result.getContent().get(0);

        assertThat(dto.getPingInterval()).isEqualTo(30);
        assertThat(dto.getTimeoutMs()).isEqualTo(2000);
        assertThat(dto.getLatencyThreshold()).isEqualTo(50.0);
    }

    @Test
    void effectiveConfig_deviceOverride_takesPrecedenceOverGroup() {
        DeviceGroup group = DeviceGroup.builder()
                .id(1L)
                .name("Group A")
                .pingInterval(40)
                .timeoutMs(3000)
                .latencyThreshold(65.0)
                .build();

        MonitoringConfig config = MonitoringConfig.builder()
                .pingInterval(10) // override
                .timeoutMs(null)
                .latencyThreshold(null)
                .build();

        Device device = Device.builder()
                .id(3L)
                .name("Device C")
                .ipAddress("192.168.1.30")
                .group(group)
                .monitoringConfig(config)
                .build();
        config.setDevice(device);

        when(deviceRepository.findFilteredDevices(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(device)));

        Page<DeviceResponseDto> result = deviceService.searchDevices(new DeviceFilterDto(), PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        DeviceResponseDto dto = result.getContent().get(0);

        assertThat(dto.getPingInterval()).isEqualTo(10); // from override
        assertThat(dto.getTimeoutMs()).isEqualTo(3000);   // from group
        assertThat(dto.getLatencyThreshold()).isEqualTo(65.0); // from group
    }

    @Test
    void discoveryEnrollDevice_doesNotHardcodeDefaults() {
        when(deviceRepository.findByIpAddressIncludingDeleted("192.168.1.50")).thenReturn(Optional.empty());

        DiscoveredDevice discovered = DiscoveredDevice.builder()
                .ipAddress("192.168.1.50")
                .macAddress("00:11:22:33:44:55")
                .deviceType(DeviceType.ROUTER)
                .build();

        DiscoveryOutcome outcome = discoveryTransactionService.processDiscoveredDevice(discovered, "192.168.1.0/24");
        assertThat(outcome).isEqualTo(DiscoveryOutcome.ADDED);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());

        Device savedDevice = captor.getValue();
        assertThat(savedDevice.getMonitoringConfig()).isNotNull();
        assertThat(savedDevice.getMonitoringConfig().getPingInterval()).isNull();
        assertThat(savedDevice.getMonitoringConfig().getTimeoutMs()).isNull();
        assertThat(savedDevice.getMonitoringConfig().getLatencyThreshold()).isNull();
    }

    @Test
    void updateDevice_nonExistentGroupId_throwsResourceNotFoundException() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());

        DeviceFormDto formDto = DeviceFormDto.builder()
                .id(1L)
                .name("Router 1")
                .ipAddress("192.168.1.1")
                .deviceType(DeviceType.ROUTER)
                .groupId(999L)
                .build();

        assertThatThrownBy(() -> deviceService.saveDevice(formDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ID: 999");
    }

    @Test
    void searchDevices_sortWhitelistFallback() {
        DeviceFilterDto filter = new DeviceFilterDto();
        Pageable invalidSortPageable = PageRequest.of(0, 10, Sort.by("maliciousProperty; DROP TABLE"));

        when(deviceRepository.findFilteredDevices(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<DeviceResponseDto> result = deviceService.searchDevices(filter, invalidSortPageable);

        assertThat(result).isNotNull();
        verify(deviceRepository).findFilteredDevices(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void toggleMaintenanceMode_resetsHealthEvaluator() {
        Device device = Device.builder()
                .id(1L)
                .name("Server-1")
                .ipAddress("10.0.0.1")
                .status(DeviceStatus.ONLINE)
                .isMonitored(true)
                .build();

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        // Switch to MAINTENANCE
        deviceService.toggleMaintenanceMode(1L);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
        verify(healthEvaluator).resetCounters(1L);

        // Switch back from MAINTENANCE
        deviceService.toggleMaintenanceMode(1L);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.UNKNOWN);
        verify(healthEvaluator, times(2)).resetCounters(1L);
    }
}
