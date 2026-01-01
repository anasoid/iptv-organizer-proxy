-- Add source-level proxy configuration fields
ALTER TABLE sources
ADD COLUMN enableproxy TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Enable HTTP proxy for upstream requests from this source',
ADD COLUMN disablestreamproxy TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Disable /proxy endpoint for redirects - return direct redirect URLs to client';
