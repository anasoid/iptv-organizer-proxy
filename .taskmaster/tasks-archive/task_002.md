# Task ID: 2

**Title:** Database Schema & Migration System

**Status:** pending

**Dependencies:** 1

**Priority:** high

**Description:** Create complete database schema for both MySQL and SQLite with all tables, indexes, and foreign keys. Implement migration system to create and update database structure

**Details:**

1. Create migration system:
   - `migrations/001_create_admin_users.sql` (MySQL)
   - `migrations/001_create_admin_users_sqlite.sql` (SQLite)
   - Repeat for all tables: sources, clients, filters, categories, live_streams, vod_streams, series, sync_logs, connection_logs
2. Admin users table:
   - id (PRIMARY KEY AUTO_INCREMENT)
   - username (VARCHAR(100) UNIQUE NOT NULL)
   - password_hash (VARCHAR(255) NOT NULL)
   - email (VARCHAR(255))
   - is_active (BOOLEAN DEFAULT 1)
   - created_at, updated_at, last_login (DATETIME)
3. Sources table:
   - id, name, url, username, password
   - sync_interval (INT), last_sync, next_sync (DATETIME)
   - sync_status (ENUM: idle, syncing, error)
   - is_active (BOOLEAN)
4. Clients table:
   - id, source_id (FK), filter_id (FK, nullable)
   - username (UNIQUE), password, name, email
   - expiry_date, is_active, hide_adult_content (BOOLEAN DEFAULT 1)
   - max_connections, created_at, last_login, notes
5. Filters table:
   - id, name, description, filter_config (TEXT for YAML)
6. Categories table:
   - id (PRIMARY KEY), source_id (FK), category_id (INT functional ID)
   - UNIQUE KEY (source_id, category_id, category_type)
   - category_name, category_type (ENUM: live, vod, series)
   - parent_id (INT nullable), labels (TEXT)
   - created_at
7. Live streams table:
   - id (PRIMARY KEY), source_id (FK), stream_id (INT)
   - name, category_id (INT), category_ids (TEXT JSON)
   - FOREIGN KEY (source_id, category_id) REFERENCES categories(source_id, category_id)
   - is_adult (BOOLEAN), labels (TEXT), is_active
   - data (JSON for complete API response)
   - created_at, updated_at
8. VOD streams and Series tables (same structure as live_streams)
9. Sync logs table:
   - id, source_id (FK), sync_type (ENUM: live_categories, live_streams, vod_categories, vod_streams, series_categories, series)
   - started_at, completed_at, status, items_added, items_updated, items_deleted, error_message
10. Connection logs table:
    - id, client_id (FK), action, ip_address, user_agent, created_at
11. Create indexes as per PRD appendix
12. Create CLI migration runner: `bin/migrate.php`
    - Detects DB_TYPE and runs appropriate migrations
    - Tracks migration history
    - Supports up/down migrations

**Test Strategy:**

1. Run migrations on MySQL database successfully
2. Run migrations on SQLite database successfully
3. Verify all tables created with correct schema
4. Test foreign key constraints work
5. Test unique constraints prevent duplicates
6. Verify indexes created for performance
7. Test migration rollback functionality
8. Validate ENUM types accept only valid values
