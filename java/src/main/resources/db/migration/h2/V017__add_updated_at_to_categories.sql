-- Add updated_at column to categories table
ALTER TABLE categories
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
