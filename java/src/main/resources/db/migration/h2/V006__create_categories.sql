CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(500) NOT NULL,
    category_type VARCHAR(20) NOT NULL CHECK (category_type IN ('live', 'vod', 'series')),
    num INT DEFAULT 0,
    allow_deny VARCHAR(10) CHECK (allow_deny IS NULL OR allow_deny IN ('allow', 'deny')),
    parent_id INT,
    labels TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT unique_category UNIQUE (source_id, category_id, category_type)
);

CREATE INDEX IF NOT EXISTS idx_categories_source_category_num ON categories(source_id, category_id, num);
CREATE INDEX IF NOT EXISTS idx_categories_allow_deny ON categories(allow_deny);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
