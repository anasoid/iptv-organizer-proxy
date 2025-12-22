-- Categories table for MySQL
-- Stores stream categories with composite unique key

CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    category_id INT NOT NULL COMMENT 'Functional category ID from source',
    category_name VARCHAR(500) NOT NULL,
    category_type ENUM('live', 'vod', 'series') NOT NULL,
    num INT DEFAULT 0 COMMENT 'Order number assigned during synchronization (starting from 1)',
    allow_deny ENUM('allow', 'deny') COMMENT 'Explicit allow/deny override for this category',
    parent_id INT NULL,
    labels TEXT COMMENT 'Comma-separated extracted labels',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY uk_source_category (source_id, category_id, category_type),
    INDEX idx_source_category_num (source_id, category_type, num),
    INDEX idx_categories_allow_deny (source_id, allow_deny),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
