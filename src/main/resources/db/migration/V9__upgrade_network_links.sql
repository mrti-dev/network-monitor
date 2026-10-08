-- ==============================================================================
-- Network Monitor Schema Migration V9: Upgrade Network Links
-- ==============================================================================

ALTER TABLE network_links
    ADD COLUMN discovery_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN last_discovered_at DATETIME NULL;

ALTER TABLE network_links
    ADD CONSTRAINT chk_link_not_self CHECK (source_device_id <> target_device_id);

CREATE INDEX idx_link_discovery_source ON network_links (discovery_source);
