-- ==============================================================================
-- Network Monitor Schema Migration V6: SNMP Monitoring
-- ==============================================================================

CREATE TABLE IF NOT EXISTS snmp_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL UNIQUE,
    version VARCHAR(10) NOT NULL DEFAULT 'V2C',
    port INT NOT NULL DEFAULT 161,
    community_encrypted VARCHAR(512) NOT NULL,
    timeout_ms INT NOT NULL DEFAULT 3000,
    retries INT NOT NULL DEFAULT 2,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_snmp_config_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_snmp_config_device (device_id),
    INDEX idx_snmp_config_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS snmp_metric_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    sys_uptime BIGINT NULL COMMENT 'hundredths of seconds',
    cpu_usage DOUBLE NULL,
    memory_usage DOUBLE NULL,
    inbound_bps DOUBLE NULL,
    outbound_bps DOUBLE NULL,
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_snmp_metric_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_snmp_metric_device_time (device_id, recorded_at),
    INDEX idx_snmp_metric_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS snmp_interface_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    interface_index INT NOT NULL,
    interface_name VARCHAR(255) NULL,
    operational_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    inbound_octets BIGINT NULL,
    outbound_octets BIGINT NULL,
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_snmp_iface_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_snmp_iface_device_time (device_id, recorded_at),
    INDEX idx_snmp_iface_device_index (device_id, interface_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
