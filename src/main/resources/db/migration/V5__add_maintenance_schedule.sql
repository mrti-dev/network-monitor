-- ==============================================================================
-- Network Monitor Schema Migration V5: Maintenance Schedule
-- ==============================================================================

CREATE TABLE IF NOT EXISTS maintenance_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    reason TEXT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    activated_at DATETIME NULL,
    completed_at DATETIME NULL,
    CONSTRAINT fk_maint_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_maint_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_maint_device_status (device_id, status),
    INDEX idx_maint_status_starts (status, starts_at),
    INDEX idx_maint_status_ends (status, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @has_maint_source := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = 'maintenance_source');
SET @sql := IF(@has_maint_source = 0, 'ALTER TABLE devices ADD COLUMN maintenance_source VARCHAR(20) NOT NULL DEFAULT \'NONE\'', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
