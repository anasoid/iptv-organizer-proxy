-- Sync logs table for MySQL
-- Stores synchronization operation history

CREATE TABLE IF NOT EXISTS sync_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    sync_type ENUM('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series') NOT NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    status ENUM('running', 'completed', 'failed') NOT NULL DEFAULT 'running',
    items_added INT NOT NULL DEFAULT 0,
    items_updated INT NOT NULL DEFAULT 0,
    items_deleted INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    duration_seconds INTEGER DEFAULT 0,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    INDEX idx_source_status_started (source_id, status, started_at),
    INDEX idx_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
