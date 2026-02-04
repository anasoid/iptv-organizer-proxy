-- Add new proxy control boolean columns to sources table
ALTER TABLE sources
ADD COLUMN enable_proxy BOOLEAN;

ALTER TABLE sources
ADD COLUMN enable_tunnel BOOLEAN;

-- Add new connection mode enum columns to sources table
ALTER TABLE sources
ADD COLUMN connect_xtream_api VARCHAR(20) DEFAULT 'DEFAULT';

ALTER TABLE sources
ADD COLUMN connect_xtream_stream VARCHAR(20) DEFAULT 'DEFAULT';

ALTER TABLE sources
ADD COLUMN connect_xmltv VARCHAR(20) DEFAULT 'DEFAULT';

-- Add new proxy control boolean columns to clients table
ALTER TABLE clients
ADD COLUMN enable_proxy BOOLEAN;

ALTER TABLE clients
ADD COLUMN enable_tunnel BOOLEAN;

-- Add new connection mode enum columns to clients table
ALTER TABLE clients
ADD COLUMN connect_xtream_api VARCHAR(20) DEFAULT 'INHERITED';

ALTER TABLE clients
ADD COLUMN connect_xtream_stream VARCHAR(20) DEFAULT 'INHERITED';

ALTER TABLE clients
ADD COLUMN connect_xmltv VARCHAR(20) DEFAULT 'INHERITED';
