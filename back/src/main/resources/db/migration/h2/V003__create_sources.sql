CREATE TABLE IF NOT EXISTS sources (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    sync_interval INT NOT NULL DEFAULT 1,
    last_sync TIMESTAMP NULL,
    next_sync TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    enableproxy BOOLEAN NOT NULL DEFAULT FALSE,
    disablestreamproxy BOOLEAN NOT NULL DEFAULT FALSE,
    stream_follow_location BOOLEAN NOT NULL DEFAULT TRUE,
    use_redirect BOOLEAN,
    use_redirect_xmltv BOOLEAN,
    enable_proxy BOOLEAN NOT NULL DEFAULT FALSE,
    enable_tunnel BOOLEAN NOT NULL DEFAULT FALSE,
    connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    proxy_id INT NULL,
    black_list_filter TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_is_active ON sources(is_active);
CREATE INDEX IF NOT EXISTS idx_source_next_sync ON sources(next_sync);
CREATE INDEX IF NOT EXISTS idx_source_proxy_id ON sources(proxy_id);
