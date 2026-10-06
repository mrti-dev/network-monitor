package com.network.network_monitor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.network.network_monitor.entity.Device;
import com.network.network_monitor.entity.MaintenanceSchedule;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.DeviceStatus;
import com.network.network_monitor.enums.DeviceType;
import com.network.network_monitor.enums.MaintenanceSource;
import com.network.network_monitor.enums.MaintenanceStatus;
import com.network.network_monitor.enums.Role;

@DataJpaTest
@ActiveProfiles("test")
class MaintenanceScheduleRepositoryDataJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MaintenanceScheduleRepository scheduleRepository;

    private Device testDevice;
    private User testUser;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.of(2026, 10, 6, 12, 0, 0);

        testUser = em.persistFlushFind(User.builder()
                .username("admin_maint")
                .password("$2a$10$hash")
                .role(Role.ADMIN)
                .build());

        testDevice = em.persistFlushFind(Device.builder()
                .name("Core Switch Maint")
                .ipAddress("192.168.100.1")
                .deviceType(DeviceType.SWITCH)
                .status(DeviceStatus.ONLINE)
                .maintenanceSource(MaintenanceSource.NONE)
                .isMonitored(true)
                .isDeleted(false)
                .build());
    }

    @Test
    @DisplayName("findOverlappingSchedules: phát hiện chính xác lịch bảo trì trùng lặp thời gian")
    void testFindOverlappingSchedules() {
        MaintenanceSchedule existing = em.persistFlushFind(MaintenanceSchedule.builder()
                .device(testDevice)
                .title("Bảo trì tuần")
                .startsAt(now.plusHours(2))
                .endsAt(now.plusHours(4))
                .status(MaintenanceStatus.SCHEDULED)
                .createdBy(testUser)
                .build());

        // Trường hợp 1: Trùng lặp một phần (bắt đầu trước, kết thúc trong khoảng)
        List<MaintenanceSchedule> overlaps1 = scheduleRepository.findOverlappingSchedules(
                testDevice.getId(), now.plusHours(1), now.plusHours(3), null, Set.of(MaintenanceStatus.SCHEDULED));
        assertThat(overlaps1).hasSize(1);
        assertThat(overlaps1.get(0).getId()).isEqualTo(existing.getId());

        // Trường hợp 2: Trùng lặp nằm hoàn toàn bên trong
        List<MaintenanceSchedule> overlaps2 = scheduleRepository.findOverlappingSchedules(
                testDevice.getId(), now.plusHours(2).plusMinutes(30), now.plusHours(3).plusMinutes(30), null, Set.of(MaintenanceStatus.SCHEDULED));
        assertThat(overlaps2).hasSize(1);

        // Trường hợp 3: Hoàn toàn không trùng lặp (kết thúc trước khi existing bắt đầu)
        List<MaintenanceSchedule> overlaps3 = scheduleRepository.findOverlappingSchedules(
                testDevice.getId(), now, now.plusHours(2), null, Set.of(MaintenanceStatus.SCHEDULED));
        assertThat(overlaps3).isEmpty();

        // Trường hợp 4: Có excludeId chính nó khi update
        List<MaintenanceSchedule> overlaps4 = scheduleRepository.findOverlappingSchedules(
                testDevice.getId(), now.plusHours(2), now.plusHours(4), existing.getId(), Set.of(MaintenanceStatus.SCHEDULED));
        assertThat(overlaps4).isEmpty();
    }

    @Test
    @DisplayName("findDueScheduledSchedules và findDueActiveSchedules: lọc đúng các lịch đến hạn")
    void testFindDueSchedules() {
        // Lịch SCHEDULED đã đến giờ bắt đầu
        MaintenanceSchedule dueScheduled = em.persistFlushFind(MaintenanceSchedule.builder()
                .device(testDevice)
                .title("Lịch bắt đầu ngay")
                .startsAt(now.minusMinutes(10))
                .endsAt(now.plusHours(1))
                .status(MaintenanceStatus.SCHEDULED)
                .createdBy(testUser)
                .build());

        // Lịch ACTIVE đã đến giờ kết thúc
        MaintenanceSchedule dueActive = em.persistFlushFind(MaintenanceSchedule.builder()
                .device(testDevice)
                .title("Lịch kết thúc ngay")
                .startsAt(now.minusHours(2))
                .endsAt(now.minusMinutes(5))
                .status(MaintenanceStatus.ACTIVE)
                .createdBy(testUser)
                .build());

        List<MaintenanceSchedule> dueScheduledList = scheduleRepository.findDueScheduledSchedules(now);
        assertThat(dueScheduledList).extracting(MaintenanceSchedule::getId).contains(dueScheduled.getId());

        List<MaintenanceSchedule> dueActiveList = scheduleRepository.findDueActiveSchedules(now);
        assertThat(dueActiveList).extracting(MaintenanceSchedule::getId).contains(dueActive.getId());
    }

    @Test
    @DisplayName("countActiveSchedulesExcluding: đếm đúng số lịch ACTIVE khác")
    void testCountActiveSchedulesExcluding() {
        MaintenanceSchedule active1 = em.persistFlushFind(MaintenanceSchedule.builder()
                .device(testDevice)
                .title("Active 1")
                .startsAt(now.minusHours(1))
                .endsAt(now.plusHours(1))
                .status(MaintenanceStatus.ACTIVE)
                .createdBy(testUser)
                .build());

        long count = scheduleRepository.countActiveSchedulesExcluding(testDevice.getId(), active1.getId());
        assertThat(count).isEqualTo(0L);

        MaintenanceSchedule active2 = em.persistFlushFind(MaintenanceSchedule.builder()
                .device(testDevice)
                .title("Active 2")
                .startsAt(now.minusMinutes(30))
                .endsAt(now.plusHours(2))
                .status(MaintenanceStatus.ACTIVE)
                .createdBy(testUser)
                .build());

        long countAfter = scheduleRepository.countActiveSchedulesExcluding(testDevice.getId(), active1.getId());
        assertThat(countAfter).isEqualTo(1L);
    }
}
