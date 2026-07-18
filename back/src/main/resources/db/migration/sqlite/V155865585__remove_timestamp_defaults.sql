ALTER TABLE admin_users DROP COLUMN created_at;
ALTER TABLE admin_users DROP COLUMN updated_at;
ALTER TABLE admin_users ADD COLUMN created_at TIMESTAMP;
ALTER TABLE admin_users ADD COLUMN updated_at TIMESTAMP;
UPDATE admin_users SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE proxies DROP COLUMN created_at;
ALTER TABLE proxies DROP COLUMN updated_at;
ALTER TABLE proxies ADD COLUMN created_at TIMESTAMP;
ALTER TABLE proxies ADD COLUMN updated_at TIMESTAMP;
UPDATE proxies SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE filters DROP COLUMN created_at;
ALTER TABLE filters DROP COLUMN updated_at;
ALTER TABLE filters ADD COLUMN created_at TIMESTAMP;
ALTER TABLE filters ADD COLUMN updated_at TIMESTAMP;
UPDATE filters SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE sources DROP COLUMN created_at;
ALTER TABLE sources DROP COLUMN updated_at;
ALTER TABLE sources ADD COLUMN created_at TIMESTAMP;
ALTER TABLE sources ADD COLUMN updated_at TIMESTAMP;
UPDATE sources SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE clients DROP COLUMN created_at;
ALTER TABLE clients DROP COLUMN updated_at;
ALTER TABLE clients ADD COLUMN created_at TIMESTAMP;
ALTER TABLE clients ADD COLUMN updated_at TIMESTAMP;
UPDATE clients SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE categories DROP COLUMN created_at;
ALTER TABLE categories DROP COLUMN updated_at;
ALTER TABLE categories ADD COLUMN created_at TIMESTAMP;
ALTER TABLE categories ADD COLUMN updated_at TIMESTAMP;
UPDATE categories SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE live_streams DROP COLUMN created_at;
ALTER TABLE live_streams DROP COLUMN updated_at;
ALTER TABLE live_streams ADD COLUMN created_at TIMESTAMP;
ALTER TABLE live_streams ADD COLUMN updated_at TIMESTAMP;
UPDATE live_streams SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE vod_streams DROP COLUMN created_at;
ALTER TABLE vod_streams DROP COLUMN updated_at;
ALTER TABLE vod_streams ADD COLUMN created_at TIMESTAMP;
ALTER TABLE vod_streams ADD COLUMN updated_at TIMESTAMP;
UPDATE vod_streams SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE series DROP COLUMN created_at;
ALTER TABLE series DROP COLUMN updated_at;
ALTER TABLE series ADD COLUMN created_at TIMESTAMP;
ALTER TABLE series ADD COLUMN updated_at TIMESTAMP;
UPDATE series SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

ALTER TABLE sync_logs DROP COLUMN created_at;
ALTER TABLE sync_logs DROP COLUMN updated_at;
ALTER TABLE sync_logs ADD COLUMN created_at TIMESTAMP;
ALTER TABLE sync_logs ADD COLUMN updated_at TIMESTAMP;
UPDATE sync_logs SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP;

DROP INDEX IF EXISTS idx_connection_logs_client_created;
ALTER TABLE connection_logs DROP COLUMN created_at;
ALTER TABLE connection_logs ADD COLUMN created_at TIMESTAMP;
UPDATE connection_logs SET created_at = CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_connection_logs_client_created ON connection_logs(client_id, created_at);
