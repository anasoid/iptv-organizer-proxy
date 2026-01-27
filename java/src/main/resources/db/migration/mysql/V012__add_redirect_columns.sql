-- Add redirect and proxy columns to clients table
ALTER TABLE clients
ADD COLUMN use_redirect TINYINT(1) NULL DEFAULT NULL AFTER hide_adult_content,
ADD COLUMN use_redirect_xmltv TINYINT(1) NULL DEFAULT NULL AFTER use_redirect,
ADD COLUMN enableproxy TINYINT(1) NULL DEFAULT NULL AFTER use_redirect_xmltv,
ADD COLUMN disablestreamproxy TINYINT(1) NULL DEFAULT NULL AFTER enableproxy,
ADD COLUMN stream_follow_location TINYINT(1) NULL DEFAULT NULL AFTER disablestreamproxy;

-- Modify existing columns in sources table to allow NULL (enableProxy defaults to 0, disableStreamProxy defaults to 1, streamFollowLocation defaults to 0)
ALTER TABLE sources
MODIFY COLUMN enableproxy TINYINT(1) NULL DEFAULT 0,
MODIFY COLUMN disablestreamproxy TINYINT(1) NULL DEFAULT 1,
MODIFY COLUMN stream_follow_location TINYINT(1) NULL DEFAULT 0;

-- Add redirect columns to sources table
ALTER TABLE sources
ADD COLUMN use_redirect TINYINT(1) NULL DEFAULT NULL AFTER stream_follow_location,
ADD COLUMN use_redirect_xmltv TINYINT(1) NULL DEFAULT NULL AFTER use_redirect;
