CREATE TABLE IF NOT EXISTS filters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    filter_config TEXT NOT NULL,
    use_source_filter INTEGER NOT NULL DEFAULT 1 CHECK(use_source_filter IN (0, 1)),
    favoris TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_filter_name ON filters(name);
