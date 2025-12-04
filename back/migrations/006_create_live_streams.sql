-- Live streams table for MySQL
-- Stores live TV stream information

CREATE TABLE IF NOT EXISTS live_streams (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    stream_id INT NOT NULL COMMENT 'Functional stream ID from source',
    name VARCHAR(500) NOT NULL,
    category_id INT NOT NULL COMMENT 'Primary category ID',
    category_ids TEXT COMMENT 'JSON array of all category IDs',
    is_adult TINYINT(1) NOT NULL DEFAULT 0,
    labels TEXT COMMENT 'Comma-separated extracted labels',
    data JSON COMMENT 'Complete API response data',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY uk_source_stream (source_id, stream_id),
    INDEX idx_source_id (source_id),
    INDEX idx_category_id (category_id),
    INDEX idx_is_adult (is_adult),
    INDEX idx_name (name(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
