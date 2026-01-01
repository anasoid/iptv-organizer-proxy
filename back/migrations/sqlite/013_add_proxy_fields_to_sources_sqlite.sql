-- Add source-level proxy configuration fields
ALTER TABLE sources ADD COLUMN enableproxy INTEGER NOT NULL DEFAULT 0 CHECK(enableproxy IN (0, 1));
ALTER TABLE sources ADD COLUMN disablestreamproxy INTEGER NOT NULL DEFAULT 0 CHECK(disablestreamproxy IN (0, 1));
