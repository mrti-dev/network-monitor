package com.network.network_monitor.service;

import java.util.List;

import com.network.network_monitor.dto.TcpPortDto;
import com.network.network_monitor.dto.TcpPortFormDto;
import com.network.network_monitor.strategy.TcpProbeResult;

public interface TcpPortMonitorService {

    List<TcpPortDto> getPortsByDeviceId(Long deviceId);

    TcpPortDto getPortById(Long id);

    TcpPortDto createPort(Long deviceId, TcpPortFormDto form);

    TcpPortDto updatePort(Long id, TcpPortFormDto form);

    void togglePort(Long id);

    void deletePort(Long id);

    void evaluateProbeResult(Long monitorId, TcpProbeResult result);
}
