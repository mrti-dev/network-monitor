-- ==============================================================================
-- Network Monitor Schema Migration V4: TCP Port Monitoring
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tcp_port_monitors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    port INT NOT NULL,
    service_name VARCHAR(100) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_ms INT NOT NULL DEFAULT 3000,
    failure_threshold INT NOT NULL DEFAULT 3,
    success_threshold INT NOT NULL DEFAULT 2,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    consecutive_failures INT NOT NULL DEFAULT 0,
    consecutive_successes INT NOT NULL DEFAULT 0,
    last_checked_at DATETIME NULL,
    last_status_changed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_tcp_port_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uq_device_port UNIQUE (device_id, port),
    INDEX idx_tcp_port_device (device_id),
    INDEX idx_tcp_port_status (status),
    INDEX idx_tcp_port_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_port := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'alerts' AND column_name = 'port');
SET @sql := IF(@has_port = 0, 'ALTER TABLE alerts ADD COLUMN port INT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'alerts' AND index_name = 'idx_alert_device_port_status');
SET @sql := IF(@has_idx = 0, 'CREATE INDEX idx_alert_device_port_status ON alerts (device_id, alert_type, port, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
