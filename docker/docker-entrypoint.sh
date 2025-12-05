#!/bin/sh

set -e

echo "Starting IPTV Organizer Proxy..."

# Ensure DB_TYPE is set (default to sqlite)
export DB_TYPE="${DB_TYPE:-sqlite}"
echo "Database type: $DB_TYPE"

# Wait for database to be ready (if using external DB)
if [ ! -z "$DB_HOST" ]; then
    echo "Waiting for database at $DB_HOST:${DB_PORT:-3306}..."
    while ! nc -z "$DB_HOST" "${DB_PORT:-3306}"; do
        sleep 1
    done
    echo "Database is ready!"
fi

# Create SQLite database directory and fix permissions
if [ "$DB_TYPE" = "sqlite" ]; then
    mkdir -p /app/data
    echo "SQLite database directory: /app/data"
    # Ensure the app user can write to the data directory
    chown -R app:app /app/data 2>&1 || true
    chmod -R 777 /app/data 2>&1 || true

    # Fix any existing database files
    echo "Fixing existing database file permissions..."
    chmod 666 /app/data/*.sqlite 2>&1 || true
    chown app:app /app/data/*.sqlite 2>&1 || true
fi

# Create logs directory
mkdir -p /app/logs
# Allow app user to write to logs
chown -R app:app /app/logs 2>&1 || true
chmod -R 777 /app/logs 2>&1 || true

# Run migrations
echo "Running database migrations..."
php /app/bin/migrate.php || true

# Fix database file permissions after migration
if [ "$DB_TYPE" = "sqlite" ]; then
    echo "Setting final database file permissions..."
    chmod 666 /app/data/*.sqlite 2>&1 || true
    chown app:app /app/data/*.sqlite 2>&1 || true
    echo "Database permissions fixed"
fi

# Start PHP-FPM in background
echo "Starting PHP-FPM..."
php-fpm &

# Start Nginx in foreground (main process)
echo "Starting Nginx..."
exec nginx -g "daemon off;"
