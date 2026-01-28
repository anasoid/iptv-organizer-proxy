CREATE TABLE IF NOT EXISTS proxies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    proxy_url TEXT,
    proxy_host TEXT,
    proxy_port INTEGER,
    proxy_type TEXT CHECK(proxy_type IN ('HTTP', 'HTTPS', 'SOCKS5')),
    proxy_username TEXT,
    proxy_password TEXT,
    timeout INTEGER,
    max_retries INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proxy_name ON proxies(name);
CREATE INDEX IF NOT EXISTS idx_proxy_type ON proxies(proxy_type);

CREATE TRIGGER IF NOT EXISTS update_proxies_updated_at
AFTER UPDATE ON proxies
FOR EACH ROW
BEGIN
    UPDATE proxies SET updated_at = datetime('now') WHERE id = NEW.id;
END;
