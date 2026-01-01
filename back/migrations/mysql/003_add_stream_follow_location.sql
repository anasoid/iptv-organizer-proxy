-- Migration: Add stream_follow_location to sources table
-- Description: Adds per-source configuration for following HTTP redirects when streaming

ALTER TABLE sources
ADD COLUMN stream_follow_location TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Follow HTTP redirects when streaming from this source';
