CREATE TABLE IF NOT EXISTS proxies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    proxy_url VARCHAR(255),
    proxy_host VARCHAR(255),
    proxy_port INT,
    proxy_type VARCHAR(10) CHECK(proxy_type IN ('HTTP', 'HTTPS', 'SOCKS5')),
    proxy_username VARCHAR(255),
    proxy_password VARCHAR(255),
    timeout INT,
    max_retries INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proxy_name ON proxies(name);
CREATE INDEX IF NOT EXISTS idx_proxy_type ON proxies(proxy_type);
