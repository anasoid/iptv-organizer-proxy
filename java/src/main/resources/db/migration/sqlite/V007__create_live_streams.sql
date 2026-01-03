CREATE TABLE IF NOT EXISTS live_streams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL,
    num INTEGER DEFAULT 0,
    allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')),
    name TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    category_ids TEXT,
    is_adult INTEGER NOT NULL DEFAULT 0 CHECK(is_adult IN (0, 1)),
    labels TEXT,
    data TEXT,
    added_date DATE,
    release_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE (source_id, stream_id)
);

CREATE INDEX IF NOT EXISTS idx_live_source_category_num ON live_streams(source_id, category_id, num);
CREATE INDEX IF NOT EXISTS idx_live_source_num ON live_streams(source_id, num);
CREATE INDEX IF NOT EXISTS idx_live_allow_deny ON live_streams(allow_deny);
CREATE INDEX IF NOT EXISTS idx_live_added_date ON live_streams(added_date);
CREATE INDEX IF NOT EXISTS idx_live_release_date ON live_streams(release_date);

CREATE TRIGGER IF NOT EXISTS update_live_streams_updated_at
AFTER UPDATE ON live_streams
FOR EACH ROW
BEGIN
    UPDATE live_streams SET updated_at = datetime('now') WHERE id = NEW.id;
END;
