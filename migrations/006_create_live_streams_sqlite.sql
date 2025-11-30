-- Live streams table for SQLite
-- Stores live TV stream information

CREATE TABLE IF NOT EXISTS live_streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL, -- Functional stream ID from source
    name TEXT NOT NULL,
    category_id INTEGER NOT NULL, -- Primary category ID
    category_ids TEXT, -- JSON array of all category IDs
    is_adult INTEGER NOT NULL DEFAULT 0 CHECK(is_adult IN (0, 1)),
    labels TEXT, -- Comma-separated extracted labels
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    data TEXT, -- Complete API response data (JSON)
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE(source_id, stream_id)
);

CREATE INDEX IF NOT EXISTS idx_live_streams_source_id ON live_streams(source_id);
CREATE INDEX IF NOT EXISTS idx_live_streams_category_id ON live_streams(category_id);
CREATE INDEX IF NOT EXISTS idx_live_streams_is_adult ON live_streams(is_adult);
CREATE INDEX IF NOT EXISTS idx_live_streams_is_active ON live_streams(is_active);
CREATE INDEX IF NOT EXISTS idx_live_streams_name ON live_streams(name);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_live_streams_updated_at
AFTER UPDATE ON live_streams
BEGIN
    UPDATE live_streams SET updated_at = datetime('now') WHERE id = NEW.id;
END;
