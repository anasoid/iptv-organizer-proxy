-- Add num column to categories table for ordering
-- This field stores the order number assigned during synchronization (1, 2, 3...)

ALTER TABLE categories ADD COLUMN num INT DEFAULT 0 AFTER category_type;

-- Create index for ordering queries
CREATE INDEX idx_source_category_num ON categories(source_id, category_type, num);
