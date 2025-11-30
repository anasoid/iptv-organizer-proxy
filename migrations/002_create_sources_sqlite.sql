-- Sources table for SQLite
-- Stores upstream IPTV source server information

CREATE TABLE IF NOT EXISTS sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    sync_interval INTEGER NOT NULL DEFAULT 3600, -- Sync interval in seconds
    last_sync TEXT,
    next_sync TEXT,
    sync_status TEXT NOT NULL DEFAULT 'idle' CHECK(sync_status IN ('idle', 'syncing', 'error')),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_sources_is_active ON sources(is_active);
CREATE INDEX IF NOT EXISTS idx_sources_sync_status ON sources(sync_status);
CREATE INDEX IF NOT EXISTS idx_sources_next_sync ON sources(next_sync);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_sources_updated_at
AFTER UPDATE ON sources
BEGIN
    UPDATE sources SET updated_at = datetime('now') WHERE id = NEW.id;
END;
