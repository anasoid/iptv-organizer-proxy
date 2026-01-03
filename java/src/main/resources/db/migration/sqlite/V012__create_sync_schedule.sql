CREATE TABLE IF NOT EXISTS sync_schedule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    task_type TEXT NOT NULL CHECK(task_type IN ('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series')),
    next_sync TIMESTAMP NOT NULL,
    last_sync TIMESTAMP,
    sync_interval INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE (source_id, task_type)
);

CREATE INDEX IF NOT EXISTS idx_sync_schedule_next_sync ON sync_schedule(next_sync);

CREATE TRIGGER IF NOT EXISTS update_sync_schedule_updated_at
AFTER UPDATE ON sync_schedule
FOR EACH ROW
BEGIN
    UPDATE sync_schedule SET updated_at = datetime('now') WHERE id = NEW.id;
END;
