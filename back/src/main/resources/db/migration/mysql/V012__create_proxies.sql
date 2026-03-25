CREATE TABLE IF NOT EXISTS proxies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    proxy_url VARCHAR(255),
    proxy_host VARCHAR(255),
    proxy_port INT,
    proxy_type ENUM('HTTP', 'HTTPS', 'SOCKS5'),
    proxy_username VARCHAR(255),
    proxy_password VARCHAR(255),
    timeout INT,
    max_retries INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_proxy_name (name),
    INDEX idx_proxy_type (proxy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
