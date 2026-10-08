-- ==============================================================================
-- Network Monitor Schema Migration V8: SLA Report & Targets
-- ==============================================================================

CREATE TABLE IF NOT EXISTS sla_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    target_uptime_percent DOUBLE NOT NULL DEFAULT 99.5,
    max_latency_ms DOUBLE NOT NULL DEFAULT 100.0,
    max_packet_loss_percent DOUBLE NOT NULL DEFAULT 1.0,
    device_id BIGINT NULL,
    group_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_sla_target_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_sla_target_group FOREIGN KEY (group_id) REFERENCES device_groups(id) ON DELETE CASCADE,
    INDEX idx_sla_target_device (device_id),
    INDEX idx_sla_target_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
