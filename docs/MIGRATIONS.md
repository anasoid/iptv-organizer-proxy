# Database Migrations

## Task 2: Database Schema & Migration System - Completed ✓

Complete database schema with dual-database support (MySQL and SQLite) and migration tracking system.

## Migration Files Created

### Tracking System
- `000_create_migrations_table.sql` / `000_create_migrations_table_sqlite.sql`

### Table Migrations (10 tables, 20 files)

1. **admin_users** - Admin panel authentication
   - `001_create_admin_users.sql` / `001_create_admin_users_sqlite.sql`

2. **sources** - IPTV source servers
   - `002_create_sources.sql` / `002_create_sources_sqlite.sql`

3. **filters** - YAML-based filter configurations
   - `003_create_filters.sql` / `003_create_filters_sqlite.sql`

4. **clients** - End-user credentials
   - `004_create_clients.sql` / `004_create_clients_sqlite.sql`

5. **categories** - Stream categories
   - `005_create_categories.sql` / `005_create_categories_sqlite.sql`

6. **live_streams** - Live TV streams
   - `006_create_live_streams.sql` / `006_create_live_streams_sqlite.sql`

7. **vod_streams** - Video on demand
   - `007_create_vod_streams.sql` / `007_create_vod_streams_sqlite.sql`

8. **series** - TV series
   - `008_create_series.sql` / `008_create_series_sqlite.sql`

9. **sync_logs** - Synchronization history
   - `009_create_sync_logs.sql` / `009_create_sync_logs_sqlite.sql`

10. **connection_logs** - Client connection tracking
    - `010_create_connection_logs.sql` / `010_create_connection_logs_sqlite.sql`

**Total: 22 migration files**

## Running Migrations

### Prerequisites
- PHP 8.1+ installed
- Database created (MySQL) or directory writable (SQLite)
- `.env` file configured with database credentials

### Execute Migrations

```bash
# Run all pending migrations
php bin/migrate.php
```

### Migration Output Example

```
→ Starting database migrations...
→ Database type: MYSQL
→ Creating migrations tracking table...
✓ Migrations tracking table created
→ Found 0 previously executed migrations
→ Found 10 pending migrations
→ Executing migration: 001_create_admin_users
✓ Migration executed: 001_create_admin_users
→ Executing migration: 002_create_sources
✓ Migration executed: 002_create_sources
...
→ Migration summary:
✓ Executed: 10
✓ All migrations completed successfully!
```

## Database Schema

### Key Features

**MySQL vs SQLite Differences Handled:**

| Feature | MySQL | SQLite |
|---------|-------|--------|
| Auto-increment | `AUTO_INCREMENT` | `AUTOINCREMENT` |
| Boolean | `TINYINT(1)` | `INTEGER CHECK(col IN (0,1))` |
| ENUM | `ENUM('a','b')` | `TEXT CHECK(col IN ('a','b'))` |
| JSON | `JSON` column type | `TEXT` (JSON string) |
| Datetime | `DATETIME` | `TEXT` (ISO8601) |
| Timestamps | `ON UPDATE CURRENT_TIMESTAMP` | Triggers |

**ENUM Constraints:**
- `sources.sync_status`: `idle`, `syncing`, `error`
- `categories.category_type`: `live`, `vod`, `series`
- `sync_logs.sync_type`: `live_categories`, `live_streams`, `vod_categories`, `vod_streams`, `series_categories`, `series`
- `sync_logs.status`: `running`, `completed`, `failed`

**Composite Keys:**
- `categories.UNIQUE(source_id, category_id, category_type)`
- `live_streams.UNIQUE(source_id, stream_id)`
- `vod_streams.UNIQUE(source_id, stream_id)`
- `series.UNIQUE(source_id, stream_id)`

**Foreign Key Relationships:**
- `clients.source_id` → `sources.id` (CASCADE DELETE)
- `clients.filter_id` → `filters.id` (SET NULL)
- `categories.source_id` → `sources.id` (CASCADE DELETE)
- `live_streams.source_id` → `sources.id` (CASCADE DELETE)
- `vod_streams.source_id` → `sources.id` (CASCADE DELETE)
- `series.source_id` → `sources.id` (CASCADE DELETE)
- `sync_logs.source_id` → `sources.id` (CASCADE DELETE)
- `connection_logs.client_id` → `clients.id` (CASCADE DELETE)

**Indexes Created:**
- Primary keys on all `id` columns
- Foreign key indexes for performance
- `username` indexes (admin_users, clients)
- `is_active` indexes for filtering
- `sync_status`, `sync_type` for sync operations
- `created_at` indexes for log queries
- `name` indexes on stream tables

## Migration System Features

### ✅ Implemented
- Automatic database type detection (MySQL/SQLite)
- Migration tracking (prevents duplicate runs)
- Sequential execution (sorted by filename)
- Transaction support (rollback on error)
- Colored terminal output
- Error handling and detailed error messages
- Skip previously executed migrations

### 🔜 Future Enhancements
- Rollback support (`bin/migrate.php --rollback`)
- Migration status command (`bin/migrate.php --status`)
- Force re-run (`bin/migrate.php --force`)
- Fresh migrations (`bin/migrate.php --fresh`)

## Verification Commands

Once migrations are run in a PHP environment:

```bash
# For MySQL
mysql -u root -p iptv_proxy -e "SHOW TABLES;"
mysql -u root -p iptv_proxy -e "DESCRIBE admin_users;"
mysql -u root -p iptv_proxy -e "SELECT * FROM migrations;"

# For SQLite
sqlite3 data/database.sqlite ".tables"
sqlite3 data/database.sqlite ".schema admin_users"
sqlite3 data/database.sqlite "SELECT * FROM migrations;"
```

## Testing the Schema

### Test Foreign Keys

```sql
-- MySQL
INSERT INTO sources (name, url, username, password) VALUES ('Test Source', 'http://test.com', 'user', 'pass');
INSERT INTO clients (source_id, username, password) VALUES (1, 'testclient', 'testpass');

-- Try to delete source (should cascade delete client)
DELETE FROM sources WHERE id = 1;

-- Verify client was deleted
SELECT * FROM clients WHERE id = 1; -- Should return no rows
```

### Test ENUM Constraints

```sql
-- Should fail (invalid sync_status)
INSERT INTO sources (name, url, username, password, sync_status)
VALUES ('Test', 'http://test.com', 'user', 'pass', 'invalid');

-- Should succeed
INSERT INTO sources (name, url, username, password, sync_status)
VALUES ('Test', 'http://test.com', 'user', 'pass', 'idle');
```

### Test Unique Constraints

```sql
-- Should fail (duplicate username)
INSERT INTO clients (source_id, username, password) VALUES (1, 'testclient', 'pass1');
INSERT INTO clients (source_id, username, password) VALUES (1, 'testclient', 'pass2');
```

## Next Steps

Proceed to **Task 3: Data Models & ORM Implementation** to create PHP model classes for all tables.
