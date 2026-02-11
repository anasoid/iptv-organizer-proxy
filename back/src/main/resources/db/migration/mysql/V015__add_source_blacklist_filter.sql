-- Add blacklist filter column to sources table
ALTER TABLE sources ADD COLUMN black_list_filter LONGTEXT NULL;
