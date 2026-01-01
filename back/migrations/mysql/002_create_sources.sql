-- Sources table for MySQL
-- Stores upstream IPTV source server information

CREATE TABLE IF NOT EXISTS sources (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    sync_interval INT NOT NULL DEFAULT 1 COMMENT 'Sync interval in days',
    last_sync DATETIME NULL,
    next_sync DATETIME NULL,
    sync_status ENUM('idle', 'syncing', 'error') NOT NULL DEFAULT 'idle',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    enableproxy TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Enable HTTP proxy for upstream requests from this source',
    disablestreamproxy TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Disable /proxy endpoint for redirects - return direct redirect URLs to client',
    stream_follow_location TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Follow HTTP redirects when streaming from this source',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_is_active_status (is_active, sync_status),
    INDEX idx_next_sync (next_sync)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
