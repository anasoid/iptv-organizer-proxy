-- VOD streams table for SQLite
-- Stores video-on-demand content information

CREATE TABLE IF NOT EXISTS vod_streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL, -- Functional stream ID from source
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

CREATE INDEX IF NOT EXISTS idx_vod_streams_source_id ON vod_streams(source_id);
CREATE INDEX IF NOT EXISTS idx_vod_streams_category_id ON vod_streams(category_id);
CREATE INDEX IF NOT EXISTS idx_vod_streams_is_adult ON vod_streams(is_adult);
CREATE INDEX IF NOT EXISTS idx_vod_streams_name ON vod_streams(name);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_vod_streams_updated_at
AFTER UPDATE ON vod_streams
BEGIN
    UPDATE vod_streams SET updated_at = datetime('now') WHERE id = NEW.id;
END;
