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


-- Insert default admin user
-- Username: admin
-- Password: nimda$123 (BCrypt hashed with cost factor 10)
-- Email: admin@iptv-organizer.local
INSERT OR IGNORE INTO admin_users (username, password_hash, email, is_active, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$HQw4.xDjswR5u.qq28vovOC4oHiZjWfgIUbCv4n5MPmoK.6EMBlGS',
    'admin@iptv-organizer.local',
    1,
    datetime('now'),
    datetime('now')
);
