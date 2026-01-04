IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'admin_users')
BEGIN
    CREATE TABLE admin_users (
        id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(255) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        email VARCHAR(255),
        is_active BIT NOT NULL DEFAULT 1,
        created_at DATETIME2 DEFAULT GETDATE(),
        updated_at DATETIME2 DEFAULT GETDATE(),
        last_login DATETIME2 NULL
    );
    CREATE INDEX idx_admin_is_active ON admin_users(is_active);
END;

-- Insert default admin user if not exists
IF NOT EXISTS (SELECT 1 FROM admin_users WHERE username = 'admin')
BEGIN
    INSERT INTO admin_users (username, password_hash, email, is_active, created_at, updated_at)
    VALUES (
        'admin',
        '$2a$10$HQw4.xDjswR5u.qq28vovOC4oHiZjWfgIUbCv4n5MPmoK.6EMBlGS',
        'admin@iptv-organizer.local',
        1,
        GETDATE(),
        GETDATE()
    );
END;
