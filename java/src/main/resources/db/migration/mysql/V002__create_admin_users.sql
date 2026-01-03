CREATE TABLE IF NOT EXISTS admin_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login DATETIME NULL,
    INDEX idx_admin_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default admin user
-- Username: admin
-- Password: nimda$123 (BCrypt hashed with cost factor 10)
-- Email: admin@iptv-organizer.local
INSERT IGNORE INTO admin_users (username, password_hash, email, is_active, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$HQw4.xDjswR5u.qq28vovOC4oHiZjWfgIUbCv4n5MPmoK.6EMBlGS',
    'admin@iptv-organizer.local',
    1,
    NOW(),
    NOW()
);
