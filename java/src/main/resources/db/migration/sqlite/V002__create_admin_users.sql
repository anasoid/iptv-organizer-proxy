CREATE TABLE IF NOT EXISTS admin_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    email TEXT,
    is_active INTEGER NOT NULL DEFAULT 1 CHECK(is_active IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_is_active ON admin_users(is_active);

CREATE TRIGGER IF NOT EXISTS update_admin_users_updated_at
AFTER UPDATE ON admin_users
FOR EACH ROW
BEGIN
    UPDATE admin_users SET updated_at = datetime('now') WHERE id = NEW.id;
END;
