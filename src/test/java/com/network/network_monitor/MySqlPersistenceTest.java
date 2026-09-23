package com.network.network_monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.network.network_monitor.config.MonitoringDefaults;
import com.network.network_monitor.dto.*;
import com.network.network_monitor.entity.*;
import com.network.network_monitor.enums.*;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.notification.NotificationDispatcher;
import com.network.network_monitor.repository.*;
import com.network.network_monitor.scheduler.HealthEvaluator;
import com.network.network_monitor.service.impl.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DeviceServiceImpl.class, DiscoveryTransactionService.class, AlertServiceImpl.class,
        NotificationDispatcher.class, MonitoringDefaults.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
class MySqlPersistenceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("MYSQL_TEST_URL");
        if (!url.matches("jdbc:mysql://[^/]+/network_monitor_test_[a-zA-Z0-9_]+\\?.*")) {
            throw new IllegalArgumentException("Chỉ cho phép database network_monitor_test_* dành riêng cho kiểm thử");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getenv("MYSQL_TEST_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("MYSQL_TEST_PASSWORD"));
    }

    @MockitoSpyBean DeviceRepository devices;
    @Autowired DeviceLogRepository logs;
    @Autowired MetricLogRepository metrics;
    @Autowired AlertRepository alerts;
    @Autowired DeviceServiceImpl service;
    @Autowired DiscoveryTransactionService discovery;
    @Autowired AlertServiceImpl alertService;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManagerFactory factory;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean HealthEvaluator health;
    @MockitoSpyBean NotificationLogRepository notifications;

    private Device device() {
        return devices.saveAndFlush(Device.builder().ipAddress(UUID.randomUUID().toString())
                .name("test").status(DeviceStatus.UNKNOWN).build());
    }

    private void transaction(Runnable action) {
        new TransactionTemplate(manager).executeWithoutResult(status -> action.run());
    }

    @Test
    void softDeletePreservesConfigAndManualRestorePreservesSettings() {
        String ip = UUID.randomUUID().toString();
        service.saveDevice(DeviceFormDto.builder().ipAddress(ip).name("old").build());
        Device original = devices.findByIpAddress(ip).orElseThrow();
        jdbc.update("update monitoring_configs set latency_threshold=321 where device_id=?", original.getId());
        assertThat(service.getLatencyThreshold(original.getId())).isEqualTo(321);
        assertThat(service.getDeviceFormById(original.getId()).getName()).isEqualTo("old");
        service.deleteDevice(original.getId());
        assertThat(devices.findById(original.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select version from devices where id=?", Integer.class, original.getId()))
                .isEqualTo(original.getVersion() + 1);
        assertThat(jdbc.queryForObject("select count(*) from monitoring_configs where device_id=?", Integer.class, original.getId())).isEqualTo(1);
        service.saveDevice(DeviceFormDto.builder().ipAddress(ip).name("restored").location("lab")
                .macAddress("AA:BB:CC:DD:EE:FF").deviceType(DeviceType.values()[0]).build());
        Device restored = devices.findById(original.getId()).orElseThrow();
        assertThat(restored.getName()).isEqualTo("restored");
        assertThat(restored.getLocation()).isEqualTo("lab");
        assertThat(restored.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:FF");
        assertThat(restored.getStatus()).isEqualTo(DeviceStatus.UNKNOWN);
        assertThat(restored.getIsMonitored()).isTrue();
        assertThat(service.getLatencyThreshold(original.getId())).isEqualTo(321);
        assertThat(logs.findByDeviceIdAndAction(original.getId(), DeviceLogAction.REACTIVATE)).hasSize(1);
        assertThatThrownBy(() -> service.saveDevice(DeviceFormDto.builder().ipAddress(ip).name("duplicate").build()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void missingConfigUsesDefaultsAndIsRecreatedOnRestore() {
        Device device = device();
        assertThat(service.getLatencyThreshold(device.getId())).isEqualTo(new MonitoringDefaults().getLatencyThreshold());
        service.deleteDevice(device.getId());
        assertThat(discovery.processDiscoveredDevice(ScanResponseDto.DiscoveredDevice.builder()
                .ipAddress(device.getIpAddress()).build(), "test")).isEqualTo(DiscoveryOutcome.REACTIVATED);
        assertThat(jdbc.queryForObject("select count(*) from monitoring_configs where device_id=?", Integer.class, device.getId())).isEqualTo(1);
    }

    @Test
    void rollbackDeleteLeavesNoLogAndStaleDeleteIsDetected() {
        Device device = device();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.deleteDevice(device.getId());
            status.setRollbackOnly();
        });
        assertThat(devices.findById(device.getId())).isPresent();
        assertThat(logs.findByDeviceIdAndAction(device.getId(), DeviceLogAction.SOFT_DELETE)).isEmpty();
        assertThatThrownBy(() -> transaction(() -> {
            Device stale = devices.findById(device.getId()).orElseThrow();
            jdbc.update("update devices set version=version+1 where id=?", stale.getId());
            devices.delete(stale);
            devices.flush();
        })).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(logs.findByDeviceIdAndAction(device.getId(), DeviceLogAction.SOFT_DELETE)).isEmpty();
    }

    @Test
    void sqlDeleteUsesIdThenVersionAndIncrementsVersion() {
        Device device = device();
        transaction(() -> devices.deleteById(device.getId()));
        assertThat(jdbc.queryForObject("select is_deleted from devices where id=?", Boolean.class, device.getId())).isTrue();
        assertThat(jdbc.queryForObject("select version from devices where id=?", Integer.class, device.getId())).isEqualTo(device.getVersion() + 1);
    }

    @Test
    void deletedIpIsStillUniqueAndCannotBeAssignedToAnotherDevice() {
        Device deleted = device();
        Device other = device();
        service.deleteDevice(deleted.getId());
        assertThatThrownBy(() -> service.saveDevice(DeviceFormDto.builder().id(other.getId())
                .ipAddress(deleted.getIpAddress()).name("collision").build())).isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> devices.saveAndFlush(Device.builder().ipAddress(deleted.getIpAddress())
                .status(DeviceStatus.UNKNOWN).build())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void latestMetricsAreLimitedAndStableWithoutLoadingDevice() {
        Device device = device();
        LocalDateTime time = LocalDateTime.of(2026, 9, 1, 0, 0);
        for (int i = 0; i < 35; i++) {
            metrics.saveAndFlush(MetricLog.builder().device(device).recordedAt(time.plusSeconds(i / 5))
                    .latencyMs((double) i).isReachable(i % 2 == 0).build());
        }
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.getDeviceMetrics(device.getId());
        assertThat(result).hasSize(30);
        assertThat(result).extracting(MetricLogDto::getLatencyMs)
                .containsExactlyElementsOf(java.util.stream.IntStream.iterate(34, i -> i - 1).limit(30).mapToObj(i -> (double) i).toList());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(metrics.deleteByRecordedAtBefore(time.plusSeconds(1))).isEqualTo(5);
        assertThat(service.getDeviceMetrics(device.getId())).hasSize(30);
    }

    @Test
    void alertProjectionIncludesDeletedDevicesWithStableOrderAndNoNPlusOne() {
        Device device = device();
        LocalDateTime time = LocalDateTime.of(2026, 9, 1, 0, 0);
        var first = alerts.saveAndFlush(Alert.builder().device(device).alertType(AlertType.OFFLINE)
                .severity(AlertSeverity.CRITICAL).message("first").triggeredAt(time).build());
        var second = alerts.saveAndFlush(Alert.builder().device(device).alertType(AlertType.OFFLINE)
                .severity(AlertSeverity.CRITICAL).message("second").triggeredAt(time).build());
        service.deleteDevice(device.getId());
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var page = alertService.getAllAlerts(PageRequest.of(0, 100));
        var matching = page.getContent().stream().filter(a -> a.getDeviceId().equals(device.getId())).toList();
        assertThat(matching).extracting(AlertDto::getId).containsExactly(second.getId(), first.getId());
        assertThat(matching).allMatch(AlertDto::getIsDeviceDeleted);
        assertThat(page.getTotalElements()).isEqualTo(alerts.count());
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        statistics.clear();
        assertThat(alertService.getAllAlerts(PageRequest.of(0, 1)).getTotalElements()).isEqualTo(page.getTotalElements());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void notificationRunsAfterCommitAndNeverForRollback() {
        Device device = device();
        transaction(() -> {
            alertService.triggerAlert(device, "commit", AlertType.OFFLINE);
            assertThat(jdbc.queryForObject("select count(*) from notification_logs n join alerts a on n.alert_id=a.id where a.device_id=?", Integer.class, device.getId())).isZero();
        });
        assertThat(jdbc.queryForList("select n.status from notification_logs n join alerts a on n.alert_id=a.id where a.device_id=?", String.class, device.getId()))
                .containsExactly("NOT_IMPLEMENTED");
        Device rollback = device();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            alertService.triggerAlert(rollback, "rollback", AlertType.OFFLINE);
            status.setRollbackOnly();
        });
        assertThat(alerts.findByDeviceIdAndStatus(rollback.getId(), AlertStatus.TRIGGERED)).isEmpty();
        verify(notifications, times(1)).save(any());
    }

    @Test
    void notificationFailureDoesNotRollbackCommittedAlert() {
        doThrow(new DataIntegrityViolationException("notification failed")).when(notifications).save(any());
        Device device = device();
        assertThatCode(() -> alertService.triggerAlert(device, "keep alert", AlertType.OFFLINE)).doesNotThrowAnyException();
        assertThat(alerts.findByDeviceIdAndStatus(device.getId(), AlertStatus.TRIGGERED)).hasSize(1);
    }

    @Test
    void notificationCommitFailureDoesNotEscapeOrRollbackAlert() {
        doAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new DataIntegrityViolationException("notification commit failed");
                }
            });
            return invocation.getArgument(0);
        }).when(notifications).save(any());
        Device device = device();
        assertThatCode(() -> alertService.triggerAlert(device, "commit failure", AlertType.OFFLINE)).doesNotThrowAnyException();
        assertThat(alerts.findByDeviceIdAndStatus(device.getId(), AlertStatus.TRIGGERED)).hasSize(1);
    }

    @Test
    void concurrentDiscoveryRestoresOnce() throws Exception {
        Device device = device();
        service.deleteDevice(device.getId());
        CyclicBarrier start = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = entityManager.createNativeQuery("SELECT * FROM devices WHERE ip_address = :ip", Device.class)
                    .setParameter("ip", device.getIpAddress()).getResultStream().findFirst();
            start.await(10, TimeUnit.SECONDS);
            return result;
        }).when(devices).findByIpAddressIncludingDeleted(device.getIpAddress());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<DiscoveryOutcome> task = () -> {
                try {
                    return discovery.processDiscoveredDevice(ScanResponseDto.DiscoveredDevice.builder()
                            .ipAddress(device.getIpAddress()).build(), "test");
                } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                    return DiscoveryOutcome.SKIPPED;
                }
            };
            var a = executor.submit(task);
            var b = executor.submit(task);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(DiscoveryOutcome.REACTIVATED, DiscoveryOutcome.SKIPPED);
        }
        assertThat(logs.findByDeviceIdAndAction(device.getId(), DeviceLogAction.REACTIVATE)).hasSize(1);
    }

    @Test
    void concurrentManualRestoreDoesNotOverwriteWinningMetadata() throws Exception {
        Device device = device();
        service.deleteDevice(device.getId());
        CyclicBarrier loaded = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = entityManager.createNativeQuery("SELECT * FROM devices WHERE ip_address = :ip", Device.class)
                    .setParameter("ip", device.getIpAddress()).getResultStream().findFirst();
            loaded.await(10, TimeUnit.SECONDS);
            return result;
        }).when(devices).findByIpAddressIncludingDeleted(device.getIpAddress());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of("first", "second").stream().map(name -> executor.submit(() -> {
                try {
                    service.saveDevice(DeviceFormDto.builder().ipAddress(device.getIpAddress()).name(name).build());
                    return name;
                } catch (OptimisticLockingFailureException e) {
                    return "conflict";
                }
            })).toList();
            var outcomes = List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
            assertThat(outcomes).contains("conflict");
            String winner = outcomes.stream().filter(s -> !s.equals("conflict")).findFirst().orElseThrow();
            assertThat(devices.findById(device.getId()).orElseThrow().getName()).isEqualTo(winner);
        }
        assertThat(logs.findByDeviceIdAndAction(device.getId(), DeviceLogAction.REACTIVATE)).hasSize(1);
    }
}
