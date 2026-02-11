-- Add blackList column to categories table
ALTER TABLE categories ADD COLUMN black_list TEXT NOT NULL DEFAULT 'default'
  CHECK(black_list IN ('default', 'hide', 'visible', 'force_hide', 'force_visible'));

-- Add blackListFilter column to sources table
ALTER TABLE sources ADD COLUMN black_list_filter TEXT;

-- Create index for blackList filtering
CREATE INDEX IF NOT EXISTS idx_categories_black_list ON categories(black_list);
