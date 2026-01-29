CREATE TABLE IF NOT EXISTS filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    filter_config TEXT NOT NULL,
    use_source_filter INTEGER NOT NULL DEFAULT 1 CHECK(use_source_filter IN (0, 1)),
    favoris TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_filter_name ON filters(name);

CREATE TRIGGER IF NOT EXISTS update_filters_updated_at
AFTER UPDATE ON filters
FOR EACH ROW
BEGIN
    UPDATE filters SET updated_at = datetime('now') WHERE id = NEW.id;
END;
