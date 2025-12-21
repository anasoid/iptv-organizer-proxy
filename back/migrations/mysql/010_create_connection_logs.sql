-- Connection logs table for MySQL
-- Stores client connection activity

CREATE TABLE IF NOT EXISTS connection_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    client_id INT NOT NULL,
    action VARCHAR(100) NOT NULL COMMENT 'API action or endpoint accessed',
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    INDEX idx_client_created (client_id, created_at),
    INDEX idx_ip_address (ip_address)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
