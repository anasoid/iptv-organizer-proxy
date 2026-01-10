CREATE TABLE IF NOT EXISTS sources (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    sync_interval INT NOT NULL DEFAULT 1,
    last_sync DATETIME NULL,
    next_sync DATETIME NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    enableproxy TINYINT(1) NOT NULL DEFAULT 0,
    disablestreamproxy TINYINT(1) NOT NULL DEFAULT 0,
    stream_follow_location TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_is_active (is_active),
    INDEX idx_source_next_sync (next_sync)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
