CREATE TABLE IF NOT EXISTS clients (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_id INT NOT NULL,
    filter_id INT,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    expiry_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    hide_adult_content BOOLEAN NOT NULL DEFAULT TRUE,
    max_connections INT NOT NULL DEFAULT 1,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    FOREIGN KEY (source_id) REFERENCES sources(id) ON DELETE CASCADE,
    FOREIGN KEY (filter_id) REFERENCES filters(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_client_source_is_active ON clients(source_id, is_active);
CREATE INDEX IF NOT EXISTS idx_client_filter_id ON clients(filter_id);
CREATE INDEX IF NOT EXISTS idx_client_expiry_date ON clients(expiry_date);
