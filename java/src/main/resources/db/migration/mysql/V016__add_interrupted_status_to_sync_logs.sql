-- Add 'interrupted' status to sync_logs status enum
-- Used when application is restarted during active sync

ALTER TABLE sync_logs MODIFY status ENUM('running', 'completed', 'failed', 'interrupted') NOT NULL DEFAULT 'running';
