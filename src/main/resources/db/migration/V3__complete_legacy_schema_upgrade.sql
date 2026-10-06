SET @exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'devices' AND column_name = 'group_id');
SET @sql := IF(@exists = 0, 'ALTER TABLE devices ADD COLUMN group_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'alerts' AND column_name = 'acknowledged_at');
SET @sql := IF(@exists = 0, 'ALTER TABLE alerts ADD COLUMN acknowledged_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'devices' AND index_name = 'idx_device_group');
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_device_group ON devices (group_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'alerts' AND index_name = 'idx_alert_status');
SET @sql := IF(@exists = 0, 'CREATE INDEX idx_alert_status ON alerts (status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND table_name = 'devices' AND constraint_name = 'fk_device_group');
SET @sql := IF(@exists = 0, 'ALTER TABLE devices ADD CONSTRAINT fk_device_group FOREIGN KEY (group_id) REFERENCES device_groups(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
