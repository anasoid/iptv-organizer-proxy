-- Live streams table for SQLite
-- Stores live TV stream information

CREATE TABLE IF NOT EXISTS live_streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL, -- Functional stream ID from source
    num INTEGER DEFAULT 0, -- Order number assigned during synchronization (starting from 1)
    allow_deny TEXT CHECK(allow_deny IN ('allow', 'deny')), -- Explicit allow/deny override
    name TEXT NOT NULL,
    category_id INTEGER NOT NULL, -- Primary category ID
    category_ids TEXT, -- JSON array of all category IDs
    is_adult INTEGER NOT NULL DEFAULT 0 CHECK(is_adult IN (0, 1)),
    labels TEXT, -- Comma-separated extracted labels
    data TEXT, -- Complete API response data (JSON)
    added_date TEXT, -- Date when stream was added (extracted from data.added)
    release_date TEXT, -- Release date (extracted from data.releaseDate/release_date)
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE(source_id, stream_id)
);

CREATE INDEX IF NOT EXISTS idx_live_streams_source_category_num ON live_streams(source_id, category_id, num);
CREATE INDEX IF NOT EXISTS idx_live_streams_source_num ON live_streams(source_id, num);
CREATE INDEX IF NOT EXISTS idx_live_streams_allow_deny ON live_streams(source_id, allow_deny);
CREATE INDEX IF NOT EXISTS idx_live_streams_added_date ON live_streams(source_id, added_date);
CREATE INDEX IF NOT EXISTS idx_live_streams_release_date ON live_streams(source_id, release_date);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_live_streams_updated_at
AFTER UPDATE ON live_streams
BEGIN
    UPDATE live_streams SET updated_at = datetime('now') WHERE id = NEW.id;
END;
