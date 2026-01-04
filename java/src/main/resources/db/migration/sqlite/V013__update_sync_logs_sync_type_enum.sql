-- SQLite doesn't allow direct ALTER COLUMN, so we need to recreate the table
-- Save existing data
CREATE TABLE IF NOT EXISTS sync_logs_backup AS SELECT * FROM sync_logs;

-- Drop old table
DROP TABLE IF EXISTS sync_logs;

-- Create new table with updated ENUM values
CREATE TABLE IF NOT EXISTS sync_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    sync_type TEXT NOT NULL CHECK(sync_type IN ('full', 'manual_full', 'live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series', 'manual_live_categories', 'manual_live_streams', 'manual_vod_categories', 'manual_vod_streams', 'manual_series_categories', 'manual_series')),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'running' CHECK(status IN ('running', 'completed', 'failed')),
    items_added INTEGER NOT NULL DEFAULT 0,
    items_updated INTEGER NOT NULL DEFAULT 0,
    items_deleted INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_seconds INTEGER DEFAULT 0,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE
);

-- Restore data
INSERT INTO sync_logs SELECT * FROM sync_logs_backup;

-- Drop backup table
DROP TABLE sync_logs_backup;

-- Recreate indices
CREATE INDEX IF NOT EXISTS idx_sync_logs_source_status_started ON sync_logs(source_id, status, started_at);
CREATE INDEX IF NOT EXISTS idx_sync_logs_status_started ON sync_logs(status, started_at);
