CREATE TABLE IF NOT EXISTS sync_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    sync_type VARCHAR(30) NOT NULL CHECK (sync_type IN ('full', 'manual_full', 'live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series', 'manual_live_categories', 'manual_live_streams', 'manual_vod_categories', 'manual_vod_streams', 'manual_series_categories', 'manual_series')),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'completed', 'failed', 'interrupted')),
    items_added INT NOT NULL DEFAULT 0,
    items_updated INT NOT NULL DEFAULT 0,
    items_deleted INT NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_seconds INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_logs_source_status_started ON sync_logs(source_id, status, started_at);
CREATE INDEX IF NOT EXISTS idx_sync_logs_status_started ON sync_logs(status, started_at);
