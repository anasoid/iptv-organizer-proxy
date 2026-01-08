CREATE TABLE IF NOT EXISTS sources (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    sync_interval INT NOT NULL DEFAULT 1,
    last_sync TIMESTAMP NULL,
    next_sync TIMESTAMP NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'idle' CHECK (sync_status IN ('idle', 'syncing', 'error')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    enableproxy BOOLEAN NOT NULL DEFAULT FALSE,
    disablestreamproxy BOOLEAN NOT NULL DEFAULT FALSE,
    stream_follow_location BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_is_active_sync_status ON sources(is_active, sync_status);
CREATE INDEX IF NOT EXISTS idx_source_next_sync ON sources(next_sync);
