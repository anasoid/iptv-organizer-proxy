-- Add proxy_id column to sources table
ALTER TABLE sources ADD COLUMN proxy_id INTEGER REFERENCES proxies(id) ON DELETE SET NULL;

-- Create index for proxy_id
CREATE INDEX IF NOT EXISTS idx_source_proxy_id ON sources(proxy_id);
