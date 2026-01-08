CREATE TABLE IF NOT EXISTS sync_schedule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    task_type VARCHAR(25) NOT NULL CHECK (task_type IN ('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series')),
    next_sync TIMESTAMP NOT NULL,
    last_sync TIMESTAMP,
    sync_interval INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT unique_schedule UNIQUE (source_id, task_type)
);

CREATE INDEX IF NOT EXISTS idx_sync_schedule_next_sync ON sync_schedule(next_sync);
