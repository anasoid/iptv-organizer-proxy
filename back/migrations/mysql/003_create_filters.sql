-- Filters table for MySQL
-- Stores YAML-based filter configurations

CREATE TABLE IF NOT EXISTS filters (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    filter_config TEXT NOT NULL COMMENT 'YAML configuration for filter rules',
    use_source_filter TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Enable/disable filter rules checking (0=disabled, 1=enabled)',
    favoris LONGTEXT COMMENT 'Favorites/watchlist items',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
