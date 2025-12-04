-- Sync schedule table for SQLite
-- Tracks next sync time per source and task type

CREATE TABLE IF NOT EXISTS sync_schedule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    task_type TEXT NOT NULL CHECK(task_type IN ('live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series')),
    next_sync TEXT NOT NULL,
    last_sync TEXT,
    sync_interval INTEGER NOT NULL DEFAULT 3600,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE(source_id, task_type)
);

CREATE INDEX IF NOT EXISTS idx_sync_schedule_source_id ON sync_schedule(source_id);
CREATE INDEX IF NOT EXISTS idx_sync_schedule_task_type ON sync_schedule(task_type);
CREATE INDEX IF NOT EXISTS idx_sync_schedule_next_sync ON sync_schedule(next_sync);

-- Trigger to auto-update updated_at
CREATE TRIGGER IF NOT EXISTS trg_sync_schedule_updated_at
AFTER UPDATE ON sync_schedule
BEGIN
  UPDATE sync_schedule SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
