-- Sync schedule table for MySQL
-- Tracks next sync time per source and task type

CREATE TABLE IF NOT EXISTS sync_schedule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    task_type ENUM('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series') NOT NULL,
    next_sync DATETIME NOT NULL,
    last_sync DATETIME NULL,
    sync_interval INT NOT NULL DEFAULT 1 COMMENT 'Interval in days',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY unique_source_task (source_id, task_type),
    INDEX idx_source_id (source_id),
    INDEX idx_task_type (task_type),
    INDEX idx_next_sync (next_sync)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
