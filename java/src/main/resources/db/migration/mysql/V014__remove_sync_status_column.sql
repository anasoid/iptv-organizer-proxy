-- Remove sync_status column from sources table (replaced with in-memory locking)

-- Drop the composite index that includes sync_status
DROP INDEX idx_source_is_active_sync_status ON sources;

-- Remove the sync_status column
ALTER TABLE sources DROP COLUMN sync_status;

-- Create new index on is_active only
CREATE INDEX idx_source_is_active ON sources(is_active);
