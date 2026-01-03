CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    category_id INT NOT NULL,
    category_name VARCHAR(500) NOT NULL,
    category_type ENUM('live', 'vod', 'series') NOT NULL,
    num INT DEFAULT 0,
    allow_deny ENUM('allow', 'deny'),
    parent_id INT,
    labels TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    UNIQUE KEY unique_category (source_id, category_id, category_type),
    INDEX idx_categories_source_category_num (source_id, category_id, num),
    INDEX idx_categories_allow_deny (allow_deny),
    INDEX idx_categories_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
