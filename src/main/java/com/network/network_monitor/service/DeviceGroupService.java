package com.network.network_monitor.service;

import com.network.network_monitor.dto.DeviceGroupDto;
import java.util.List;

public interface DeviceGroupService {
    List<DeviceGroupDto> getAllGroups();
    DeviceGroupDto getGroupById(Long id);
    DeviceGroupDto saveGroup(DeviceGroupDto groupDto);
    void deleteGroup(Long id);
    void assignDeviceToGroup(Long deviceId, Long groupId);
}
