CREATE TABLE IF NOT EXISTS connection_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_id INTEGER NOT NULL,
    action TEXT NOT NULL,
    ip_address TEXT NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_connection_logs_client_created ON connection_logs(client_id, created_at);
CREATE INDEX IF NOT EXISTS idx_connection_logs_ip_address ON connection_logs(ip_address);
