-- Add created_at and updated_at columns to sync_logs table for audit trail
-- SQLite does not support ALTER TABLE ADD COLUMN with DEFAULT CURRENT_TIMESTAMP

ALTER TABLE sync_logs ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sync_logs ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
