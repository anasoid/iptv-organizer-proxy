-- Add num column to stream tables for ordering (SQLite version)
-- This field stores the order number assigned during synchronization (1, 2, 3...)

ALTER TABLE live_streams ADD COLUMN num INTEGER DEFAULT 0;
ALTER TABLE vod_streams ADD COLUMN num INTEGER DEFAULT 0;
ALTER TABLE series ADD COLUMN num INTEGER DEFAULT 0;

-- Create indexes for ordering queries
CREATE INDEX IF NOT EXISTS idx_source_num_live ON live_streams(source_id, num);
CREATE INDEX IF NOT EXISTS idx_source_num_vod ON vod_streams(source_id, num);
CREATE INDEX IF NOT EXISTS idx_source_num_series ON series(source_id, num);
