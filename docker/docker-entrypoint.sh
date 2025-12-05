#!/bin/sh

set -e

echo "Starting IPTV Organizer Proxy..."

# Ensure DB_TYPE is set (default to sqlite)
export DB_TYPE="${DB_TYPE:-sqlite}"
echo "Database type: $DB_TYPE"

# Set APP_PORT (default to 9090)
export APP_PORT="${APP_PORT:-9090}"
echo "Application port: $APP_PORT"

# Configure nginx to listen on APP_PORT
echo "Configuring nginx to listen on port $APP_PORT..."
sed -i "s/listen 9090;/listen $APP_PORT;/" /etc/nginx/conf.d/default.conf
sed -i "s/listen :9090;/listen :$APP_PORT;/" /etc/nginx/conf.d/default.conf

# Wait for database to be ready (if using external DB)
if [ ! -z "$DB_HOST" ]; then
    echo "Waiting for database at $DB_HOST:${DB_PORT:-3306}..."
    while ! nc -z "$DB_HOST" "${DB_PORT:-3306}"; do
        sleep 1
    done
    echo "Database is ready!"
fi

# Generate .env file if it doesn't exist
if [ ! -f /app/.env ]; then
    echo "Generating .env file..."
    JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"
    SESSION_SECRET="${SESSION_SECRET:-$(openssl rand -hex 32)}"

    cat > /app/.env << EOF
DB_TYPE=${DB_TYPE:-sqlite}
DB_SQLITE_PATH=/app/data/database.sqlite
JWT_SECRET=${JWT_SECRET}
SESSION_SECRET=${SESSION_SECRET}
CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-*}
APP_DEBUG=${APP_DEBUG:-false}
EOF

    echo ".env file created with auto-generated secrets"
else
    echo ".env file already exists, skipping generation"
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
