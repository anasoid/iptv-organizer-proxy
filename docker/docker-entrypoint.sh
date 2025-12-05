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
fi

# Run migrations
echo "Running database migrations..."
php /app/bin/migrate.php || true

# Set permissions
chown -R app:app /app/data /app/logs 2>/dev/null || true

# Run the main command
exec "$@"
