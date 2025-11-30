-- Series table for SQLite
-- Stores TV series information

CREATE TABLE IF NOT EXISTS series (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    stream_id INTEGER NOT NULL, -- Functional series ID from source
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

CREATE INDEX IF NOT EXISTS idx_series_source_id ON series(source_id);
CREATE INDEX IF NOT EXISTS idx_series_category_id ON series(category_id);
CREATE INDEX IF NOT EXISTS idx_series_is_adult ON series(is_adult);
CREATE INDEX IF NOT EXISTS idx_series_is_active ON series(is_active);
CREATE INDEX IF NOT EXISTS idx_series_name ON series(name);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_series_updated_at
AFTER UPDATE ON series
BEGIN
    UPDATE series SET updated_at = datetime('now') WHERE id = NEW.id;
END;
