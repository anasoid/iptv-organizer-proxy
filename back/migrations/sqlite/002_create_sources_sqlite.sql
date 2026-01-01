-- Sources table for SQLite
-- Stores upstream IPTV source server information

CREATE TABLE IF NOT EXISTS sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    sync_interval INTEGER NOT NULL DEFAULT 1, -- Sync interval in days
    last_sync TEXT,
    next_sync TEXT,
    sync_status TEXT NOT NULL DEFAULT 'idle' CHECK(sync_status IN ('idle', 'syncing', 'error')),
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    enableproxy INTEGER NOT NULL DEFAULT 0 CHECK(enableproxy IN (0, 1)), -- Enable HTTP proxy for upstream requests
    disablestreamproxy INTEGER NOT NULL DEFAULT 0 CHECK(disablestreamproxy IN (0, 1)), -- Disable /proxy endpoint for redirects
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_sources_is_active_status ON sources(is_active, sync_status);
CREATE INDEX IF NOT EXISTS idx_sources_next_sync ON sources(next_sync);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_sources_updated_at
AFTER UPDATE ON sources
BEGIN
    UPDATE sources SET updated_at = datetime('now') WHERE id = NEW.id;
END;
