-- Add new proxy control boolean columns and connection mode enum columns to sources table
ALTER TABLE sources
ADD COLUMN enable_proxy BOOLEAN NULL,
ADD COLUMN enable_tunnel BOOLEAN NULL,
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

-- Add new proxy control boolean columns and connection mode enum columns to clients table
ALTER TABLE clients
ADD COLUMN enable_proxy BOOLEAN NULL,
ADD COLUMN enable_tunnel BOOLEAN NULL,
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'INHERITED';
