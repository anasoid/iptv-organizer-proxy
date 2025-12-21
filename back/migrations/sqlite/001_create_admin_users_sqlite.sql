-- Admin users table for SQLite
-- Stores admin panel user credentials

CREATE TABLE IF NOT EXISTS admin_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    email TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now')),
    last_login TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_admin_username ON admin_users(username);
CREATE INDEX IF NOT EXISTS idx_admin_is_active ON admin_users(is_active);

-- Trigger to update updated_at timestamp
CREATE TRIGGER IF NOT EXISTS trg_admin_users_updated_at
AFTER UPDATE ON admin_users
BEGIN
    UPDATE admin_users SET updated_at = datetime('now') WHERE id = NEW.id;
END;
