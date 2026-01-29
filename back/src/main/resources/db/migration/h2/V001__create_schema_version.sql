CREATE TABLE IF NOT EXISTS schema_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    checksum VARCHAR(32) NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_version ON schema_version(version);
