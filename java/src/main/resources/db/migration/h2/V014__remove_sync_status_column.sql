-- Remove sync_status column from sources table (replaced with in-memory locking)

-- Drop the index that includes sync_status
DROP INDEX IF EXISTS idx_source_is_active_sync_status;

-- Remove the sync_status column
ALTER TABLE sources DROP COLUMN IF EXISTS sync_status;

-- Create new index on is_active only
CREATE INDEX IF NOT EXISTS idx_source_is_active ON sources(is_active);
