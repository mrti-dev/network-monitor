-- ==============================================================================
-- Network Monitor Schema Migration V1: Baseline Schema
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_user_username (username),
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Device Groups Table
CREATE TABLE IF NOT EXISTS device_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    ping_interval INT,
    timeout_ms INT,
    latency_threshold DOUBLE,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Devices Table
CREATE TABLE IF NOT EXISTS devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NOT NULL UNIQUE,
    mac_address VARCHAR(17),
    device_type VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    location VARCHAR(255),
    group_id BIGINT,
    is_monitored BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_device_group FOREIGN KEY (group_id) REFERENCES device_groups(id) ON DELETE SET NULL,
    INDEX idx_device_status (status),
    INDEX idx_device_type (device_type),
    INDEX idx_device_ip (ip_address),
    INDEX idx_device_monitored (is_monitored),
    INDEX idx_device_group (group_id),
    INDEX idx_device_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Monitoring Configs Table
CREATE TABLE IF NOT EXISTS monitoring_configs (
    device_id BIGINT PRIMARY KEY,
    ping_interval INT,
    timeout_ms INT,
    latency_threshold DOUBLE,
    strategy_type VARCHAR(20),
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_monitoring_config_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Metric Logs Table
CREATE TABLE IF NOT EXISTS metric_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    latency_ms DOUBLE NULL,
    packet_loss DOUBLE NULL,
    is_reachable BOOLEAN NOT NULL,
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_metric_log_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_metric_device_time (device_id, recorded_at),
    INDEX idx_metric_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    acknowledged_by BIGINT,
    alert_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    triggered_at DATETIME NOT NULL,
    acknowledged_at DATETIME,
    resolved_at DATETIME,
    CONSTRAINT fk_alert_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_acknowledged_by FOREIGN KEY (acknowledged_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_alert_device_status (device_id, status),
    INDEX idx_alert_triggered (triggered_at),
    INDEX idx_alert_severity (severity),
    INDEX idx_alert_type (alert_type),
    INDEX idx_alert_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Device Logs Table
CREATE TABLE IF NOT EXISTS device_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    description TEXT,
    performed_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_device_log_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_device_log_device_time (device_id, created_at),
    INDEX idx_device_log_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Notification Logs Table
CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    message TEXT,
    status VARCHAR(20) NOT NULL,
    sent_at DATETIME NOT NULL,
    CONSTRAINT fk_notification_log_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE,
    INDEX idx_notification_alert (alert_id),
    INDEX idx_notification_sent (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
