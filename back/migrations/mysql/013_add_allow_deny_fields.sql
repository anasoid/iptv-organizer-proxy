-- Add allow/deny enum field to categories and streams tables for explicit access control
-- Add use_source_filter boolean field to filters table

ALTER TABLE categories ADD COLUMN allow_deny ENUM('allow', 'deny') DEFAULT NULL AFTER num;
CREATE INDEX idx_categories_allow_deny ON categories(source_id, allow_deny);

ALTER TABLE live_streams ADD COLUMN allow_deny ENUM('allow', 'deny') DEFAULT NULL AFTER num;
CREATE INDEX idx_live_streams_allow_deny ON live_streams(source_id, allow_deny);

ALTER TABLE vod_streams ADD COLUMN allow_deny ENUM('allow', 'deny') DEFAULT NULL AFTER num;
CREATE INDEX idx_vod_streams_allow_deny ON vod_streams(source_id, allow_deny);

ALTER TABLE series ADD COLUMN allow_deny ENUM('allow', 'deny') DEFAULT NULL AFTER num;
CREATE INDEX idx_series_allow_deny ON series(source_id, allow_deny);

ALTER TABLE filters ADD COLUMN use_source_filter TINYINT(1) NOT NULL DEFAULT 1 AFTER filter_config;
