-- Add redirect and proxy columns to clients table
ALTER TABLE clients
ADD COLUMN use_redirect BOOLEAN DEFAULT NULL;

ALTER TABLE clients
ADD COLUMN use_redirect_xmltv BOOLEAN DEFAULT NULL;

ALTER TABLE clients
ADD COLUMN enableproxy BOOLEAN DEFAULT NULL;

ALTER TABLE clients
ADD COLUMN disablestreamproxy BOOLEAN DEFAULT NULL;

ALTER TABLE clients
ADD COLUMN stream_follow_location BOOLEAN DEFAULT NULL;

-- Add redirect columns to sources table
ALTER TABLE sources
ADD COLUMN use_redirect BOOLEAN DEFAULT NULL;

ALTER TABLE sources
ADD COLUMN use_redirect_xmltv BOOLEAN DEFAULT NULL;
