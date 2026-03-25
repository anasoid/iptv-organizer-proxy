# Task ID: 1

**Title:** Project Foundation & Environment Setup

**Status:** pending

**Dependencies:** None

**Priority:** high

**Description:** Initialize PHP project structure with Composer, create directory structure, configure environment variables, and set up database abstraction layer supporting both MySQL and SQLite

**Details:**

1. Initialize Composer project: `composer init`
2. Create directory structure:
   - `src/` (application code)
   - `src/Models/` (data models)
   - `src/Controllers/` (API controllers)
   - `src/Services/` (business logic)
   - `src/Database/` (database layer)
   - `src/Middleware/` (auth, CORS)
   - `config/` (configuration files)
   - `migrations/` (database migrations)
   - `bin/` (CLI scripts)
   - `public/` (web entry point)
   - `tests/` (unit tests)
3. Install dependencies:
   - `composer require slim/slim slim/psr7`
   - `composer require guzzlehttp/guzzle` (HTTP client)
   - `composer require monolog/monolog` (logging)
   - `composer require vlucas/phpdotenv` (environment variables)
   - `composer require --dev phpunit/phpunit phpstan/phpstan`
4. Create `.env` file from template with:
   - DB_TYPE (mysql or sqlite)
   - DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS (MySQL)
   - DB_SQLITE_PATH (SQLite)
   - APP_ENV, APP_DEBUG, APP_URL
   - JWT_SECRET, SESSION_SECRET
   - SYNC_ENABLED, DEFAULT_SYNC_INTERVAL
5. Create `src/Database/Connection.php` class:
   - Static method `getConnection()` returns PDO instance
   - Reads DB_TYPE from env
   - If mysql: connects to MySQL with credentials
   - If sqlite: connects to SQLite file
   - Implements singleton pattern
   - Sets PDO error mode to exceptions
6. Create `config/database.php` with connection parameters
7. Add PSR-4 autoloading in composer.json: `"App\\": "src/"`

**Test Strategy:**

1. Verify composer.json created with correct dependencies
2. Test database connection for both MySQL and SQLite
3. Run `composer install` successfully
4. Test Connection::getConnection() returns valid PDO instance
5. Verify environment variables loaded correctly
6. Run PHPStan static analysis with no errors
