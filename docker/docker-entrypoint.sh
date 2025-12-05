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
    # Ensure the app user can write to the data directory
    chown -R app:app /app/data
    chmod -R 755 /app/data
fi

# Create logs directory
mkdir -p /app/logs
# Allow app user to write to logs
chown -R app:app /app/logs
chmod 755 /app/logs

# Run migrations
echo "Running database migrations..."
php /app/bin/migrate.php || true

# Start PHP-FPM in background
echo "Starting PHP-FPM..."
php-fpm &

# Start Nginx in foreground (main process)
echo "Starting Nginx..."
exec nginx -g "daemon off;"
