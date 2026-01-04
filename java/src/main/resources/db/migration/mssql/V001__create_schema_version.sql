IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'schema_version')
BEGIN
    CREATE TABLE schema_version (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        version VARCHAR(255) NOT NULL UNIQUE,
        description VARCHAR(500),
        checksum VARCHAR(32) NOT NULL,
        applied_at DATETIME2 DEFAULT GETDATE()
    );
    CREATE INDEX idx_version ON schema_version(version);
END;
