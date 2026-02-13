CREATE TABLE IF NOT EXISTS series (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    external_id INT NOT NULL,
    num INT DEFAULT 0,
    allow_deny VARCHAR(10) CHECK (allow_deny IS NULL OR allow_deny IN ('allow', 'deny')),
    name VARCHAR(500) NOT NULL,
    category_id INT NOT NULL,
    category_ids TEXT,
    is_adult BOOLEAN NOT NULL DEFAULT FALSE,
    labels TEXT,
    data JSON,
    added_date DATE,
    release_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT unique_stream UNIQUE (source_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_series_source_category_num ON series(source_id, category_id, num);
CREATE INDEX IF NOT EXISTS idx_series_source_num ON series(source_id, num);
