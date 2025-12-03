-- Add favoris column to filters table (SQLite)
-- Separates favoris definitions from filter rules for cleaner architecture

ALTER TABLE filters
ADD COLUMN favoris TEXT DEFAULT NULL;
