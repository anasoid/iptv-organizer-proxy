-- Add duration column to sync_logs table for SQLite
-- Tracks the duration in seconds of each sync operation

ALTER TABLE sync_logs ADD COLUMN duration_seconds INTEGER DEFAULT 0;
