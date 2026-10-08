package com.network.network_monitor.service;

import java.util.List;
import com.network.network_monitor.dto.SnmpConfigDto;
import com.network.network_monitor.dto.SnmpConfigFormDto;
import com.network.network_monitor.dto.SnmpInterfaceMetricDto;
import com.network.network_monitor.dto.SnmpMetricDto;
import com.network.network_monitor.strategy.SnmpProbeResult;

public interface SnmpService {
    SnmpConfigDto getConfigByDeviceId(Long deviceId);
    SnmpConfigDto saveConfig(Long deviceId, SnmpConfigFormDto form);
    void toggleSnmp(Long deviceId);
    void deleteConfig(Long deviceId);
    SnmpProbeResult testConnection(Long deviceId);
    SnmpMetricDto getLatestMetric(Long deviceId);
    List<SnmpMetricDto> getRecentMetrics(Long deviceId, int limit);
    List<SnmpInterfaceMetricDto> getLatestInterfaces(Long deviceId);
    void processProbeResult(Long deviceId, SnmpProbeResult result);
}
