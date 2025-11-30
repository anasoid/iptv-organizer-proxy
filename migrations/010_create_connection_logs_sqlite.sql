-- Connection logs table for SQLite
-- Stores client connection activity

CREATE TABLE IF NOT EXISTS connection_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER NOT NULL,
    action TEXT NOT NULL, -- API action or endpoint accessed
    ip_address TEXT NOT NULL,
    user_agent TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_connection_logs_client_id ON connection_logs(client_id);
CREATE INDEX IF NOT EXISTS idx_connection_logs_action ON connection_logs(action);
CREATE INDEX IF NOT EXISTS idx_connection_logs_created_at ON connection_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_connection_logs_ip_address ON connection_logs(ip_address);
