-- Add num column to stream tables for ordering
-- This field stores the order number assigned during synchronization (1, 2, 3...)

ALTER TABLE live_streams ADD COLUMN num INT DEFAULT 0 AFTER stream_id;
ALTER TABLE vod_streams ADD COLUMN num INT DEFAULT 0 AFTER stream_id;
ALTER TABLE series ADD COLUMN num INT DEFAULT 0 AFTER stream_id;

-- Create indexes for ordering queries
CREATE INDEX idx_source_num ON live_streams(source_id, num);
CREATE INDEX idx_source_num ON vod_streams(source_id, num);
CREATE INDEX idx_source_num ON series(source_id, num);
