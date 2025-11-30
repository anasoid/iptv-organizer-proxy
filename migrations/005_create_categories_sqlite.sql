-- Categories table for SQLite
-- Stores stream categories with composite unique key

CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL, -- Functional category ID from source
    category_name TEXT NOT NULL,
    category_type TEXT NOT NULL CHECK(category_type IN ('live', 'vod', 'series')),
    parent_id INTEGER,
    labels TEXT, -- Comma-separated extracted labels
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE(source_id, category_id, category_type)
);

CREATE INDEX IF NOT EXISTS idx_categories_source_id ON categories(source_id);
CREATE INDEX IF NOT EXISTS idx_categories_category_type ON categories(category_type);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
