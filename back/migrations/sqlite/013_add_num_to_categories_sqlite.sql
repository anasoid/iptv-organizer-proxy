-- Add num column to categories table for ordering (SQLite version)
-- This field stores the order number assigned during synchronization (1, 2, 3...)

ALTER TABLE categories ADD COLUMN num INTEGER DEFAULT 0;

-- Create index for ordering queries
CREATE INDEX IF NOT EXISTS idx_source_category_num ON categories(source_id, category_type, num);
