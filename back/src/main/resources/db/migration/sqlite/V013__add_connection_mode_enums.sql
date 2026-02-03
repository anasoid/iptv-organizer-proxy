-- SQLite doesn't support DROP COLUMN directly, so we need to recreate tables
-- Create new sources table with enum columns and without old boolean columns
CREATE TABLE sources_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    sync_interval INTEGER NOT NULL DEFAULT 1,
    last_sync TIMESTAMP NULL,
    next_sync TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    proxy_id INTEGER NULL,
    connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migrate data from old sources table
INSERT INTO sources_new (
    id, name, url, username, password, sync_interval, last_sync, next_sync,
    is_active, proxy_id, connect_xtream_api, connect_xtream_stream, connect_xmltv,
    created_at, updated_at
)
SELECT
    id, name, url, username, password, sync_interval, last_sync, next_sync,
    is_active, proxy_id,
    CASE
        WHEN enableproxy = 1 THEN 'PROXY'
        WHEN enableproxy = 0 THEN 'TUNNEL'
        ELSE 'DEFAULT'
    END,
    CASE
        WHEN disablestreamproxy = 0 THEN 'PROXY'
        WHEN disablestreamproxy = 1 AND use_redirect = 1 THEN 'REDIRECT'
        WHEN disablestreamproxy = 1 THEN 'DIRECT'
        ELSE 'DEFAULT'
    END,
    CASE
        WHEN use_redirect_xmltv = 1 THEN 'REDIRECT'
        WHEN use_redirect_xmltv = 0 THEN 'PROXY'
        ELSE 'DEFAULT'
    END,
    created_at, updated_at
FROM sources;

-- Drop old sources table
DROP TABLE sources;

-- Rename new table
ALTER TABLE sources_new RENAME TO sources;

-- Recreate indexes for sources
CREATE INDEX idx_source_is_active ON sources(is_active);
CREATE INDEX idx_source_next_sync ON sources(next_sync);
CREATE INDEX idx_source_proxy_id ON sources(proxy_id);

-- Create new clients table with enum columns and without old boolean columns
CREATE TABLE clients_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    filter_id INTEGER,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    expiry_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    hide_adult_content BOOLEAN NOT NULL DEFAULT TRUE,
    max_connections INTEGER NOT NULL DEFAULT 1,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
    connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
    connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE SET NULL
);

-- Migrate data from old clients table (set all enum fields to INHERITED)
INSERT INTO clients_new (
    id, source_id, filter_id, username, password, name, email, expiry_date,
    is_active, hide_adult_content, max_connections, notes, created_at, updated_at,
    last_login, connect_xtream_api, connect_xtream_stream, connect_xmltv
)
SELECT
    id, source_id, filter_id, username, password, name, email, expiry_date,
    is_active, hide_adult_content, max_connections, notes, created_at, updated_at,
    last_login, 'INHERITED', 'INHERITED', 'INHERITED'
FROM clients;

-- Drop old clients table
DROP TABLE clients;

-- Rename new table
ALTER TABLE clients_new RENAME TO clients;

-- Recreate indexes for clients
CREATE INDEX idx_client_source_is_active ON clients(source_id, is_active);
CREATE INDEX idx_client_filter_id ON clients(filter_id);
CREATE INDEX idx_client_expiry_date ON clients(expiry_date);
