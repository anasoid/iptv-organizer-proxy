-- Add favoris column to filters table (MySQL)
-- Separates favoris definitions from filter rules for cleaner architecture

ALTER TABLE filters
ADD COLUMN favoris LONGTEXT DEFAULT NULL COMMENT 'YAML configuration for favoris (separate from rules)' AFTER filter_config;
