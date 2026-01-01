-- Series table for MySQL
-- Stores TV series information

CREATE TABLE IF NOT EXISTS series (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    stream_id INT NOT NULL COMMENT 'Functional series ID from source',
    num INT DEFAULT 0 COMMENT 'Order number assigned during synchronization (starting from 1)',
    allow_deny ENUM('allow', 'deny') COMMENT 'Explicit allow/deny override for this series',
    name VARCHAR(500) NOT NULL,
    category_id INT NOT NULL COMMENT 'Primary category ID',
    category_ids TEXT COMMENT 'JSON array of all category IDs',
    is_adult TINYINT(1) NOT NULL DEFAULT 0,
    labels TEXT COMMENT 'Comma-separated extracted labels',
    data JSON COMMENT 'Complete API response data',
    added_date DATE NULL COMMENT 'Date when series was added (extracted from data.added)',
    release_date DATE NULL COMMENT 'Release date (extracted from data.releaseDate/release_date)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY uk_source_stream (source_id, stream_id),
    INDEX idx_source_category_num (source_id, category_id, num),
    INDEX idx_source_num (source_id, num),
    INDEX idx_series_allow_deny (source_id, allow_deny),
    INDEX idx_series_added_date (source_id, added_date),
    INDEX idx_series_release_date (source_id, release_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
