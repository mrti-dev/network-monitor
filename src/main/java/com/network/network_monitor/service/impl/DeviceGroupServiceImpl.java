package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.DeviceGroupDto;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.DeviceGroup;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.exception.ResourceNotFoundException;
import com.network.network_monitor.repository.DeviceGroupRepository;
import com.network.network_monitor.repository.DeviceRepository;
import com.network.network_monitor.service.DeviceGroupService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceGroupServiceImpl implements DeviceGroupService {

    private final DeviceGroupRepository groupRepository;
    private final DeviceRepository deviceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceGroupDto> getAllGroups() {
        return groupRepository.findAllByOrderByNameAsc().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceGroupDto getGroupById(Long id) {
        DeviceGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm thiết bị với ID: " + id));
        return mapToDto(group);
    }

    @Override
    @Transactional
    public DeviceGroupDto saveGroup(DeviceGroupDto dto) {
        if (dto.getId() == null) {
            if (groupRepository.existsByName(dto.getName())) {
                throw new DuplicateResourceException("Nhóm '" + dto.getName() + "' đã tồn tại.");
            }
            DeviceGroup group = DeviceGroup.builder()
                    .name(dto.getName().trim())
                    .description(dto.getDescription())
                    .pingInterval(dto.getPingInterval())
                    .timeoutMs(dto.getTimeoutMs())
                    .latencyThreshold(dto.getLatencyThreshold())
                    .build();
            return mapToDto(groupRepository.save(group));
        } else {
            DeviceGroup group = groupRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + dto.getId()));

            Optional<DeviceGroup> existing = groupRepository.findByName(dto.getName().trim());
            if (existing.isPresent() && !existing.get().getId().equals(dto.getId())) {
                throw new DuplicateResourceException("Nhóm '" + dto.getName() + "' đã tồn tại.");
            }

            group.setName(dto.getName().trim());
            group.setDescription(dto.getDescription());
            group.setPingInterval(dto.getPingInterval());
            group.setTimeoutMs(dto.getTimeoutMs());
            group.setLatencyThreshold(dto.getLatencyThreshold());

            return mapToDto(groupRepository.save(group));
        }
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        DeviceGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + id));

        // Unlink all devices belonging to this group in a single bulk update
        deviceRepository.unlinkDevicesFromGroup(id);
        groupRepository.delete(group);
    }

    @Override
    @Transactional
    public void assignDeviceToGroup(Long deviceId, Long groupId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị với ID: " + deviceId));

        if (groupId == null) {
            device.setGroup(null);
        } else {
            DeviceGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm với ID: " + groupId));
            device.setGroup(group);
        }
        deviceRepository.save(device);
    }

    private DeviceGroupDto mapToDto(DeviceGroup group) {
        int count = group.getDevices() != null ? group.getDevices().size() : 0;
        return DeviceGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .pingInterval(group.getPingInterval())
                .timeoutMs(group.getTimeoutMs())
                .latencyThreshold(group.getLatencyThreshold())
                .deviceCount(count)
                .build();
    }
}
