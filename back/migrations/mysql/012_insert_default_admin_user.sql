-- Insert default admin user for MySQL
-- Username: admin
-- Password: nimda$123 (bcrypt hashed)
-- This migration creates a default admin account for initial login

INSERT INTO admin_users (username, password_hash, email, is_active)
VALUES (
    'admin',
    '$2y$12$6H2rxyVCRbcYJTYGR3vrZeEAy2BY0CQdIoLE4/x8iUdK/hAGBVn/i',
    'admin@localhost',
    1
) ON DUPLICATE KEY UPDATE updated_at = NOW();
