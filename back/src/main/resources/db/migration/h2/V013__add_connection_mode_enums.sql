-- Add new connection mode enum columns to sources table
ALTER TABLE sources
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE sources
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

ALTER TABLE sources
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'DEFAULT';

-- Add new connection mode enum columns to clients table
ALTER TABLE clients
ADD COLUMN connect_xtream_api VARCHAR(20) NOT NULL DEFAULT 'INHERITED';

ALTER TABLE clients
ADD COLUMN connect_xtream_stream VARCHAR(20) NOT NULL DEFAULT 'INHERITED';

ALTER TABLE clients
ADD COLUMN connect_xmltv VARCHAR(20) NOT NULL DEFAULT 'INHERITED';

-- Migrate existing data for sources
UPDATE sources
SET connect_xtream_api = CASE
    WHEN enableproxy = TRUE THEN 'PROXY'
    WHEN enableproxy = FALSE THEN 'TUNNEL'
    ELSE 'DEFAULT'
END
WHERE enableproxy IS NOT NULL;

UPDATE sources
SET connect_xtream_stream = CASE
    WHEN disablestreamproxy = FALSE THEN 'PROXY'
    WHEN disablestreamproxy = TRUE AND use_redirect = TRUE THEN 'REDIRECT'
    WHEN disablestreamproxy = TRUE THEN 'DIRECT'
    ELSE 'DEFAULT'
END
WHERE disablestreamproxy IS NOT NULL OR use_redirect IS NOT NULL;

UPDATE sources
SET connect_xmltv = CASE
    WHEN use_redirect_xmltv = TRUE THEN 'REDIRECT'
    WHEN use_redirect_xmltv = FALSE THEN 'PROXY'
    ELSE 'DEFAULT'
END
WHERE use_redirect_xmltv IS NOT NULL;

-- Drop old boolean columns from sources
ALTER TABLE sources
DROP COLUMN enableproxy;

ALTER TABLE sources
DROP COLUMN disablestreamproxy;

ALTER TABLE sources
DROP COLUMN use_redirect;

ALTER TABLE sources
DROP COLUMN use_redirect_xmltv;

ALTER TABLE sources
DROP COLUMN stream_follow_location;

-- Drop old boolean columns from clients
ALTER TABLE clients
DROP COLUMN enableproxy;

ALTER TABLE clients
DROP COLUMN disablestreamproxy;

ALTER TABLE clients
DROP COLUMN use_redirect;

ALTER TABLE clients
DROP COLUMN use_redirect_xmltv;

ALTER TABLE clients
DROP COLUMN stream_follow_location;
