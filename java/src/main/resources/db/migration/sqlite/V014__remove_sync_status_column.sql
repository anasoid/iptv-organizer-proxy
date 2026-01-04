-- Remove sync_status column from sources table (replaced with in-memory locking)
-- SQLite requires table recreation due to limited ALTER TABLE support

-- Create new sources table without sync_status
CREATE TABLE sources_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    username TEXT NOT NULL,
    password TEXT NOT NULL,
    sync_interval INTEGER NOT NULL DEFAULT 1,
    last_sync TIMESTAMP NULL,
    next_sync TIMESTAMP NULL,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    enableproxy INTEGER NOT NULL DEFAULT 0 CHECK(enableproxy IN (0, 1)),
    disablestreamproxy INTEGER NOT NULL DEFAULT 0 CHECK(disablestreamproxy IN (0, 1)),
    stream_follow_location INTEGER NOT NULL DEFAULT 1 CHECK(stream_follow_location IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Copy data from old table
INSERT INTO sources_new (id, name, url, username, password, sync_interval, last_sync,
                         next_sync, is_active, enableproxy, disablestreamproxy,
                         stream_follow_location, created_at, updated_at)
SELECT id, name, url, username, password, sync_interval, last_sync,
       next_sync, is_active, enableproxy, disablestreamproxy,
       stream_follow_location, created_at, updated_at
FROM sources;

-- Drop old table
DROP TABLE sources;

-- Rename new table
ALTER TABLE sources_new RENAME TO sources;

-- Create indexes
CREATE INDEX idx_source_is_active ON sources(is_active);
CREATE INDEX idx_source_next_sync ON sources(next_sync);

-- Recreate trigger
CREATE TRIGGER update_sources_updated_at
AFTER UPDATE ON sources
FOR EACH ROW
BEGIN
    UPDATE sources SET updated_at = datetime('now') WHERE id = NEW.id;
END;
