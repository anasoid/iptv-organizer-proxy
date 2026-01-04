-- Update sync_logs table to add manual sync type variants to ENUM
ALTER TABLE sync_logs MODIFY sync_type ENUM('full', 'manual_full', 'live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series', 'manual_live_categories', 'manual_live_streams', 'manual_vod_categories', 'manual_vod_streams', 'manual_series_categories', 'manual_series') NOT NULL;
