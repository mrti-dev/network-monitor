-- ==============================================================================
-- Network Monitor Schema Migration V10: Device Status History
-- ==============================================================================

CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    source VARCHAR(50) NOT NULL,
    is_active_open TINYINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT fk_status_history_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uq_device_single_open_status UNIQUE (device_id, is_active_open),
    INDEX idx_status_history_lookup (device_id, started_at, ended_at),
    INDEX idx_status_history_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Khởi tạo bản ghi ban đầu cho các thiết bị hiện có
INSERT INTO device_status_history (device_id, status, started_at, ended_at, source)
SELECT id, status, created_at, NULL, 'SYSTEM_INIT'
FROM devices
WHERE is_deleted = false;
