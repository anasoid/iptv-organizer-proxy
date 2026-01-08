CREATE TABLE IF NOT EXISTS filters (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    filter_config TEXT NOT NULL,
    use_source_filter BOOLEAN NOT NULL DEFAULT TRUE,
    favoris TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_filter_name ON filters(name);
