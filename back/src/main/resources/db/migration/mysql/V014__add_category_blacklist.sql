-- Add blackList column to categories table
ALTER TABLE categories ADD COLUMN black_list VARCHAR(20) NOT NULL DEFAULT 'default'
  CHECK(black_list IN ('default', 'hide', 'visible', 'force_hide', 'force_visible'));

-- Add blackListFilter column to sources table
ALTER TABLE sources ADD COLUMN black_list_filter TEXT;

-- Create index for blackList filtering
CREATE INDEX idx_categories_black_list ON categories(black_list);
