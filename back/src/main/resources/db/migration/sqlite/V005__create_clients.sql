CREATE TABLE IF NOT EXISTS clients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL,
    filter_id INTEGER,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    name TEXT,
    email TEXT,
    expiry_date DATE,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    hide_adult_content INTEGER NOT NULL DEFAULT 1 CHECK(hide_adult_content IN (0, 1)),
    use_redirect INTEGER,
    use_redirect_xmltv INTEGER,
    enableproxy INTEGER,
    disablestreamproxy INTEGER,
    stream_follow_location INTEGER,
    enable_proxy INTEGER CHECK(enable_proxy IS NULL OR enable_proxy IN (0, 1)),
    connect_xtream_api TEXT DEFAULT 'INHERITED',
    connect_xtream_stream TEXT DEFAULT 'INHERITED',
    connect_xmltv TEXT DEFAULT 'INHERITED',
    max_connections INTEGER NOT NULL DEFAULT 1,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_client_source_is_active ON clients(source_id, is_active);
CREATE INDEX IF NOT EXISTS idx_client_filter_id ON clients(filter_id);
CREATE INDEX IF NOT EXISTS idx_client_expiry_date ON clients(expiry_date);

