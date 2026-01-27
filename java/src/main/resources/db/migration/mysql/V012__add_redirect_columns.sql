-- Add redirect columns to clients table
ALTER TABLE clients ADD COLUMN use_redirect TINYINT(1) NULL DEFAULT NULL;
ALTER TABLE clients ADD COLUMN use_redirect_xmltv TINYINT(1) NULL DEFAULT NULL;

-- Add proxy configuration columns to clients table (for override capability)
ALTER TABLE clients ADD COLUMN enableproxy TINYINT(1) NULL;
ALTER TABLE clients ADD COLUMN disablestreamproxy TINYINT(1) NULL;
ALTER TABLE clients ADD COLUMN stream_follow_location TINYINT(1) NULL;

-- Add redirect columns to sources table
ALTER TABLE sources ADD COLUMN use_redirect TINYINT(1) NULL DEFAULT NULL;
ALTER TABLE sources ADD COLUMN use_redirect_xmltv TINYINT(1) NULL DEFAULT NULL;
