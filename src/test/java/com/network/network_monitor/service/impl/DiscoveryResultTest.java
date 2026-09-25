package com.network.network_monitor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.network.network_monitor.dto.DiscoveryOutcome;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.ScanResponseDto.DiscoveredDevice;

import com.network.network_monitor.config.DiscoveryLimits;

class DiscoveryResultTest {

    private static DiscoveryLimits testLimits() {
        DiscoveryLimits l = new DiscoveryLimits();
        l.setMaxHostsPerScan(254);
        l.setMaxConcurrentScans(3);
        l.setProbeConcurrency(4);
        l.setScanTimeoutSeconds(5);
        l.setProbeTimeoutMs(500);
        l.setArpTimeoutMs(500);
        return l;
    }

    @Test
    void callerCatchesFailureThrownByTransactionProxyAtCommit() {
        var target = mock(DiscoveryTransactionService.class);
        when(target.processDiscoveredDevice(any(), any())).thenReturn(DiscoveryOutcome.ADDED);
        var manager = mock(PlatformTransactionManager.class);
        var first = mock(TransactionStatus.class);
        var second = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(first, second);
        doThrow(new DataIntegrityViolationException("commit failed")).when(manager).commit(first);
        var attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("processDiscoveredDevice",
                new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, attributes));
        var proxy = (DiscoveryTransactionService) factory.getProxy();
        var result = new DiscoveryServiceImpl(proxy, new MonitoringDefaults(), testLimits()).persistDiscoveredDevices(
                List.of(DiscoveredDevice.builder().ipAddress("first").build(),
                        DiscoveredDevice.builder().ipAddress("second").build()), "test");
        // DataIntegrityViolationException at commit → SKIPPED (concurrent scan duplicate)
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getAdded()).isEqualTo(1);
        verify(manager).commit(second);
    }

    @Test
    void commitFailureDoesNotStopLaterIpsAndOnlyInsertIsNew() {
        var transaction = mock(DiscoveryTransactionService.class);
        when(transaction.processDiscoveredDevice(any(), eq("subnet")))
                .thenThrow(new DataIntegrityViolationException("commit constraint"))
                .thenReturn(DiscoveryOutcome.ADDED)
                .thenReturn(DiscoveryOutcome.REACTIVATED)
                .thenThrow(new OptimisticLockingFailureException("stale"))
                .thenReturn(DiscoveryOutcome.SKIPPED);
        var devices = List.of("1", "2", "3", "4", "5").stream()
                .map(ip -> DiscoveredDevice.builder().ipAddress(ip).build()).toList();
        var response = new DiscoveryServiceImpl(transaction, new MonitoringDefaults(), testLimits()).persistDiscoveredDevices(devices, "subnet");
        assertThat(response.getDiscovered()).isEqualTo(5);
        assertThat(response.getAdded()).isEqualTo(1);
        assertThat(response.getReactivated()).isEqualTo(1);
        // DataIntegrityViolationException → SKIPPED (concurrent insert)
        // OptimisticLockingFailureException → SKIPPED (concurrency failure)
        // SKIPPED from explicit return → 1
        // Total SKIPPED = 3 (ip1 DataIntegrity, ip4 OptimisticLocking, ip5 explicit SKIPPED)
        assertThat(response.getSkipped()).isEqualTo(3);
        assertThat(response.getFailed()).isEqualTo(0);
        assertThat(devices).extracting(DiscoveredDevice::isNew).containsExactly(false, true, false, false, false);
        verify(transaction, times(5)).processDiscoveredDevice(any(), eq("subnet"));
    }
}
