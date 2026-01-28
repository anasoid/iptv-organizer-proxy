-- Add proxy_id column to sources table
ALTER TABLE sources ADD COLUMN proxy_id INT NULL AFTER is_active;
ALTER TABLE sources ADD CONSTRAINT fk_source_proxy FOREIGN KEY (proxy_id) REFERENCES proxies(id) ON DELETE SET NULL;
CREATE INDEX idx_source_proxy_id ON sources(proxy_id);
