CREATE TABLE IF NOT EXISTS sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    sync_interval INTEGER NOT NULL DEFAULT 1,
    last_sync TIMESTAMP NULL,
    next_sync TIMESTAMP NULL,
    sync_status TEXT NOT NULL DEFAULT 'idle' CHECK(sync_status IN ('idle', 'syncing', 'error')),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    enableproxy INTEGER NOT NULL DEFAULT 0 CHECK(enableproxy IN (0, 1)),
    disablestreamproxy INTEGER NOT NULL DEFAULT 0 CHECK(disablestreamproxy IN (0, 1)),
    stream_follow_location INTEGER NOT NULL DEFAULT 1 CHECK(stream_follow_location IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_is_active_sync_status ON sources(is_active, sync_status);
CREATE INDEX IF NOT EXISTS idx_source_next_sync ON sources(next_sync);

CREATE TRIGGER IF NOT EXISTS update_sources_updated_at
AFTER UPDATE ON sources
FOR EACH ROW
BEGIN
    UPDATE sources SET updated_at = datetime('now') WHERE id = NEW.id;
END;
