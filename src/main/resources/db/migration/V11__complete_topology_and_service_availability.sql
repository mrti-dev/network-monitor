ALTER TABLE network_links DROP INDEX uq_link_source_target;

UPDATE network_links SET source_port = '' WHERE source_port IS NULL;
UPDATE network_links SET target_port = '' WHERE target_port IS NULL;

ALTER TABLE network_links
    MODIFY source_port VARCHAR(50) NOT NULL DEFAULT '',
    MODIFY target_port VARCHAR(50) NOT NULL DEFAULT '',
    ADD CONSTRAINT uq_link_device_interfaces
        UNIQUE (source_device_id, source_port, target_device_id, target_port);

CREATE TABLE service_availability_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    service_type VARCHAR(20) NOT NULL,
    monitor_id BIGINT NULL,
    available BOOLEAN NOT NULL,
    recorded_at DATETIME NOT NULL,
    CONSTRAINT fk_availability_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    INDEX idx_availability_device_period (device_id, service_type, recorded_at),
    INDEX idx_availability_monitor_period (monitor_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
