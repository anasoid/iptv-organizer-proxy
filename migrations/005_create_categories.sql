-- Categories table for MySQL
-- Stores stream categories with composite unique key

CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    category_id INT NOT NULL COMMENT 'Functional category ID from source',
    category_name VARCHAR(500) NOT NULL,
    category_type ENUM('live', 'vod', 'series') NOT NULL,
    parent_id INT NULL,
    labels TEXT COMMENT 'Comma-separated extracted labels',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY uk_source_category (source_id, category_id, category_type),
    INDEX idx_source_id (source_id),
    INDEX idx_category_type (category_type),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
