-- VOD streams table for SQLite
-- Stores video-on-demand content information

CREATE TABLE IF NOT EXISTS vod_streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL, -- Functional stream ID from source
    num INTEGER DEFAULT 0, -- Order number assigned during synchronization (starting from 1)
    name TEXT NOT NULL,
    category_id INTEGER NOT NULL, -- Primary category ID
    category_ids TEXT, -- JSON array of all category IDs
    is_adult INTEGER NOT NULL DEFAULT 0 CHECK(is_adult IN (0, 1)),
    labels TEXT, -- Comma-separated extracted labels
    data TEXT, -- Complete API response data (JSON)
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE(source_id, stream_id)
);

CREATE INDEX IF NOT EXISTS idx_vod_streams_source_category_num ON vod_streams(source_id, category_id, num);
CREATE INDEX IF NOT EXISTS idx_vod_streams_source_num ON vod_streams(source_id, num);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_vod_streams_updated_at
AFTER UPDATE ON vod_streams
BEGIN
    UPDATE vod_streams SET updated_at = datetime('now') WHERE id = NEW.id;
END;
