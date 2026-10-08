package com.network.network_monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("mysql-test")
class FlywayMigrationTest {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String user;

    @Value("${spring.datasource.password}")
    private String password;

    private String getFlywayTestUrl() {
        if (!url.matches("jdbc:mysql://[^/]+/network_monitor_test_[a-zA-Z0-9_]+\\?.*")) {
            throw new IllegalArgumentException("Chỉ cho phép database network_monitor_test_* dành riêng cho kiểm thử");
        }
        // Dùng schema riêng để test Flyway migration độc lập
        return url.replaceFirst("network_monitor_test_[a-zA-Z0-9_]+", "network_monitor_test_flyway_upgrade");
    }

    private String getUser() {
        return user;
    }

    private String getPassword() {
        return password;
    }

    @Test
    void testFlywayFromEmptyDatabase() {
        String url = getFlywayTestUrl();
        Flyway flyway = Flyway.configure()
                .dataSource(url, getUser(), getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("11");
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(11);
    }

    @Test
    void testFlywayUpgradeFromV8ToV11() throws Exception {
        String url = getFlywayTestUrl();
        // 1. Chạy tới version 8 (schema cũ trước khi thêm V9, V10)
        Flyway flywayV8 = Flyway.configure()
                .dataSource(url, getUser(), getPassword())
                .locations("classpath:db/migration")
                .target("8")
                .cleanDisabled(false)
                .load();

        flywayV8.clean();
        MigrateResult resultV8 = flywayV8.migrate();
        assertThat(resultV8.success).isTrue();
        assertThat(resultV8.targetSchemaVersion).isEqualTo("8");

        // 2. Nâng cấp schema V8 lên phiên bản mới nhất
        Flyway flywayLatest = Flyway.configure()
                .dataSource(url, getUser(), getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        MigrateResult resultUpgrade = flywayLatest.migrate();
        assertThat(resultUpgrade.success).isTrue();
        assertThat(resultUpgrade.targetSchemaVersion).isEqualTo("11");
        assertThat(resultUpgrade.migrationsExecuted).isEqualTo(3);

        // 3. Kiểm tra ràng buộc schema trên database thực tế
        try (var conn = java.sql.DriverManager.getConnection(url, getUser(), getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO devices (id, name, ip_address, status, is_monitored, is_deleted, version, created_at, updated_at) " +
                    "VALUES (99001, 'TestD1', '192.168.99.1', 'ONLINE', 1, 0, 0, NOW(), NOW())");

            // Kiểm tra chk_link_not_self (source_device_id <> target_device_id)
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                stmt.execute("INSERT INTO network_links (source_device_id, target_device_id, link_type, status, discovery_source, created_at, updated_at) " +
                        "VALUES (99001, 99001, 'ETHERNET', 'UP', 'MANUAL', NOW(), NOW())");
            }).isInstanceOf(java.sql.SQLException.class);

            // Kiểm tra uq_device_single_open_status: chỉ 1 trạng thái mở (ended_at IS NULL)
            stmt.execute("INSERT INTO device_status_history (device_id, status, started_at, ended_at, source) " +
                    "VALUES (99001, 'ONLINE', NOW(), NULL, 'TEST')");

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                stmt.execute("INSERT INTO device_status_history (device_id, status, started_at, ended_at, source) " +
                        "VALUES (99001, 'OFFLINE', NOW(), NULL, 'TEST')");
            }).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
