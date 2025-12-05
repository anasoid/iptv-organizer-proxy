#!/bin/sh

set -e

echo "Starting IPTV Organizer Proxy..."

# Wait for database to be ready (if using external DB)
if [ ! -z "$DB_HOST" ]; then
    echo "Waiting for database at $DB_HOST:${DB_PORT:-3306}..."
    while ! nc -z "$DB_HOST" "${DB_PORT:-3306}"; do
        sleep 1
    done
    echo "Database is ready!"
fi

# Create SQLite database directory if needed
if [ "$DB_TYPE" = "sqlite" ]; then
    mkdir -p /app/data
    echo "SQLite database directory: /app/data"
    # Ensure the app user can write to the data directory with full permissions
    chown -R app:app /app/data
    chmod -R 777 /app/data
fi

# Create logs directory
mkdir -p /app/logs
# Allow app user to write to logs with full permissions
chown -R app:app /app/logs
chmod -R 777 /app/logs

# Run migrations
echo "Running database migrations..."
php /app/bin/migrate.php || true

# Fix database file permissions after migration
if [ "$DB_TYPE" = "sqlite" ]; then
    echo "Fixing database file permissions..."
    chmod 666 /app/data/*.sqlite 2>/dev/null || true
    chown app:app /app/data/*.sqlite 2>/dev/null || true
fi

# Start PHP-FPM in background
echo "Starting PHP-FPM..."
php-fpm &

# Start Nginx in foreground (main process)
echo "Starting Nginx..."
exec nginx -g "daemon off;"
