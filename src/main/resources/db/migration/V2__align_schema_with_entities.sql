-- ==============================================================================
-- Network Monitor Schema Migration V2: Schema alignment with JPA Entities
-- ==============================================================================

-- 1. Users alignment
SET @exist := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'is_deleted');
SET @sql := IF(@exist = 0, 'ALTER TABLE users ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Devices alignment (location length=255, version INT)
ALTER TABLE devices MODIFY COLUMN location VARCHAR(255);
ALTER TABLE devices MODIFY COLUMN version INT NOT NULL DEFAULT 0;

-- 3. Device Logs alignment (performed_by, description TEXT)
SET @exist := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'device_logs' AND column_name = 'performed_by');
SET @sql := IF(@exist = 0, 'ALTER TABLE device_logs ADD COLUMN performed_by BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE device_logs MODIFY COLUMN description TEXT;

-- 4. Metric Logs alignment (nullable latency_ms & packet_loss)
ALTER TABLE metric_logs MODIFY COLUMN latency_ms DOUBLE NULL;
ALTER TABLE metric_logs MODIFY COLUMN packet_loss DOUBLE NULL;

-- 5. Notification Logs alignment (sent_at, message TEXT, index)
SET @has_created_at := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'notification_logs' AND column_name = 'created_at');
SET @has_sent_at := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'notification_logs' AND column_name = 'sent_at');

SET @sql := IF(@has_created_at > 0 AND @has_sent_at = 0, 'ALTER TABLE notification_logs CHANGE COLUMN created_at sent_at DATETIME NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE notification_logs MODIFY COLUMN message TEXT;
