-- ==============================================================================
-- Network Monitor Schema Migration V7: Network Topology Map
-- ==============================================================================

CREATE TABLE IF NOT EXISTS network_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_device_id BIGINT NOT NULL,
    target_device_id BIGINT NOT NULL,
    link_type VARCHAR(20) NOT NULL DEFAULT 'ETHERNET',
    source_port VARCHAR(50) NULL,
    target_port VARCHAR(50) NULL,
    bandwidth_mbps INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UP',
    description VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_link_source FOREIGN KEY (source_device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_link_target FOREIGN KEY (target_device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uq_link_source_target UNIQUE (source_device_id, target_device_id),
    INDEX idx_link_source (source_device_id),
    INDEX idx_link_target (target_device_id),
    INDEX idx_link_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
