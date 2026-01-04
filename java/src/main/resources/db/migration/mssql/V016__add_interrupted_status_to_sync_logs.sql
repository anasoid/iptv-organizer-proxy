-- Add 'interrupted' status to sync_logs status enum
-- Used when application is restarted during active sync

ALTER TABLE sync_logs DROP CONSTRAINT DF_sync_logs_status;

ALTER TABLE sync_logs
ALTER COLUMN status VARCHAR(50) NOT NULL DEFAULT 'running';

ALTER TABLE sync_logs
ADD CONSTRAINT CK_sync_logs_status CHECK(status IN ('running', 'completed', 'failed', 'interrupted'));

ALTER TABLE sync_logs
ADD CONSTRAINT DF_sync_logs_status DEFAULT 'running' FOR status;
