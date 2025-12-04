-- Filters table for SQLite
-- Stores YAML-based filter configurations

CREATE TABLE IF NOT EXISTS filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    filter_config TEXT NOT NULL, -- YAML configuration for filter rules and favoris
    favoris TEXT DEFAULT NULL, -- YAML configuration for favoris (separate from rules)
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_filters_name ON filters(name);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_filters_updated_at
AFTER UPDATE ON filters
BEGIN
    UPDATE filters SET updated_at = datetime('now') WHERE id = NEW.id;
END;
