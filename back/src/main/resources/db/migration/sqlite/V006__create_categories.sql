CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    external_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('live', 'vod', 'series')),
    num INTEGER DEFAULT 0,
    allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')),
    parent_id INTEGER,
    labels TEXT,
    black_list TEXT NOT NULL DEFAULT 'default' CHECK(black_list IN ('default', 'hide', 'visible', 'force_hide', 'force_visible')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE (source_id, external_id, type)
);

CREATE INDEX IF NOT EXISTS idx_categories_source_category_num ON categories(source_id, external_id, num);
CREATE INDEX IF NOT EXISTS idx_categories_allow_deny ON categories(allow_deny);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_black_list ON categories(black_list);
