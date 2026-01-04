CREATE TABLE IF NOT EXISTS sync_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    sync_type ENUM('full', 'manual_full', 'live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series', 'manual_live_categories', 'manual_live_streams', 'manual_vod_categories', 'manual_vod_streams', 'manual_series_categories', 'manual_series') NOT NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME,
    status ENUM('running', 'completed', 'failed') NOT NULL DEFAULT 'running',
    items_added INT NOT NULL DEFAULT 0,
    items_updated INT NOT NULL DEFAULT 0,
    items_deleted INT NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_seconds INT DEFAULT 0,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    INDEX idx_sync_logs_source_status_started (source_id, status, started_at),
    INDEX idx_sync_logs_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
