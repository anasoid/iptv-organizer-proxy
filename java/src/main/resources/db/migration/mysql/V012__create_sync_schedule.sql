CREATE TABLE IF NOT EXISTS sync_schedule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    task_type ENUM('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series') NOT NULL,
    next_sync DATETIME NOT NULL,
    last_sync DATETIME,
    sync_interval INT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY unique_schedule (source_id, task_type),
    INDEX idx_sync_schedule_next_sync (next_sync)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
