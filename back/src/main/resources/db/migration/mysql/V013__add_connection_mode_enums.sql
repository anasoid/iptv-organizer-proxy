-- Add new connection mode enum columns to sources table
ALTER TABLE sources
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

-- Add new connection mode enum columns to clients table
ALTER TABLE clients
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'INHERITED',
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'INHERITED';

-- Migrate existing data for sources
UPDATE sources
SET connect_xtream_api = CASE
    WHEN enableproxy = 1 THEN 'PROXY'
    WHEN enableproxy = 0 THEN 'TUNNEL'
    ELSE 'DEFAULT'
END
WHERE enableproxy IS NOT NULL;

UPDATE sources
SET connect_xtream_stream = CASE
    WHEN disablestreamproxy = 0 THEN 'PROXY'
    WHEN disablestreamproxy = 1 AND use_redirect = 1 THEN 'REDIRECT'
    WHEN disablestreamproxy = 1 THEN 'DIRECT'
    ELSE 'DEFAULT'
END
WHERE disablestreamproxy IS NOT NULL OR use_redirect IS NOT NULL;

UPDATE sources
SET connect_xmltv = CASE
    WHEN use_redirect_xmltv = 1 THEN 'REDIRECT'
    WHEN use_redirect_xmltv = 0 THEN 'PROXY'
    ELSE 'DEFAULT'
END
WHERE use_redirect_xmltv IS NOT NULL;

-- Drop old boolean columns from sources
ALTER TABLE sources
DROP COLUMN enableproxy,
DROP COLUMN disablestreamproxy,
DROP COLUMN use_redirect,
DROP COLUMN use_redirect_xmltv,
DROP COLUMN stream_follow_location;

-- Drop old boolean columns from clients
ALTER TABLE clients
DROP COLUMN enableproxy,
DROP COLUMN disablestreamproxy,
DROP COLUMN use_redirect,
DROP COLUMN use_redirect_xmltv,
DROP COLUMN stream_follow_location;
