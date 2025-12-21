-- Clients table for MySQL
-- Stores end-user credentials and assignments

CREATE TABLE IF NOT EXISTS clients (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    filter_id INT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    expiry_date DATETIME NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    hide_adult_content TINYINT(1) NOT NULL DEFAULT 1,
    max_connections INT NOT NULL DEFAULT 1,
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login DATETIME NULL,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE SET NULL,
    UNIQUE KEY uk_username (username),
    INDEX idx_source_active (source_id, is_active),
    INDEX idx_filter_id (filter_id),
    INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
