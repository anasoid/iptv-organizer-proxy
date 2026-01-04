-- Add 'interrupted' status to sync_logs status check constraint
-- Used when application is restarted during active sync
-- SQLite requires table recreation to modify constraints

BEGIN TRANSACTION;

-- Create new table with updated CHECK constraint
CREATE TABLE sync_logs_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    sync_type TEXT NOT NULL CHECK(sync_type IN ('full', 'manual_full', 'live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series', 'manual_live_categories', 'manual_live_streams', 'manual_vod_categories', 'manual_vod_streams', 'manual_series_categories', 'manual_series')),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'running' CHECK(status IN ('running', 'completed', 'failed', 'interrupted')),
    items_added INTEGER NOT NULL DEFAULT 0,
    items_updated INTEGER NOT NULL DEFAULT 0,
    items_deleted INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    duration_seconds INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE
);

-- Copy data from old table
INSERT INTO sync_logs_new SELECT * FROM sync_logs;

-- Drop old table and rename new one
DROP TABLE sync_logs;
ALTER TABLE sync_logs_new RENAME TO sync_logs;

-- Recreate indexes
CREATE INDEX IF NOT EXISTS idx_sync_logs_source_status_started ON sync_logs(source_id, status, started_at);
CREATE INDEX IF NOT EXISTS idx_sync_logs_status_started ON sync_logs(status, started_at);

COMMIT;
