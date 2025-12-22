-- Add allow/deny field to categories and streams tables for explicit access control
-- Add use_source_filter boolean field to filters table

ALTER TABLE categories ADD COLUMN allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_categories_allow_deny ON categories(source_id, allow_deny);

ALTER TABLE live_streams ADD COLUMN allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_live_streams_allow_deny ON live_streams(source_id, allow_deny);

ALTER TABLE vod_streams ADD COLUMN allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_vod_streams_allow_deny ON vod_streams(source_id, allow_deny);

ALTER TABLE series ADD COLUMN allow_deny TEXT CHECK(allow_deny IS NULL OR allow_deny IN ('allow', 'deny')) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_series_allow_deny ON series(source_id, allow_deny);

ALTER TABLE filters ADD COLUMN use_source_filter INTEGER NOT NULL DEFAULT 1 CHECK(use_source_filter IN (0, 1));
