CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    external_id INT NOT NULL,
    name VARCHAR(500) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('live', 'vod', 'series')),
    num INT DEFAULT 0,
    allow_deny VARCHAR(10) CHECK (allow_deny IS NULL OR allow_deny IN ('allow', 'deny')),
    parent_id INT,
    labels TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT unique_category UNIQUE (source_id, external_id, type)
);

CREATE INDEX IF NOT EXISTS idx_categories_source_category_num ON categories(source_id, external_id, num);
CREATE INDEX IF NOT EXISTS idx_categories_allow_deny ON categories(allow_deny);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
