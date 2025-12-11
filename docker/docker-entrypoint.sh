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

# Create logs directory and fix permissions
# Note: /logs is a Docker volume mount, so we need to ensure /logs/iptv exists inside it
mkdir -p /logs/iptv || {
    echo "ERROR: Cannot create /logs/iptv directory"
    exit 1
}

# Set ownership to app user
chown -R app:app /logs/iptv || {
    echo "ERROR: Cannot set ownership of /logs/iptv to app:app"
    exit 1
}

# Make it world-writable to ensure app user can write
chmod -R 777 /logs/iptv || {
    echo "ERROR: Cannot set permissions on /logs/iptv"
    exit 1
}

echo "Logs directory: /logs/iptv (owner: $(stat -c '%U:%G' /logs/iptv), mode: $(stat -c '%a' /logs/iptv))"

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

# Start sync daemon in background (if enabled)
if [ "${SYNC_ENABLED:-true}" = "true" ]; then
    echo "Starting sync daemon..."

    # Pre-create log file with proper ownership
    # This ensures tee doesn't get permission denied on first write
    touch /logs/iptv/sync-daemon.log
    chown app:app /logs/iptv/sync-daemon.log
    chmod 666 /logs/iptv/sync-daemon.log

    echo "Sync daemon log file: /logs/iptv/sync-daemon.log (owner: $(stat -c '%U:%G' /logs/iptv/sync-daemon.log), mode: $(stat -c '%a' /logs/iptv/sync-daemon.log))"

    # Run sync daemon as app user in background (no redirection, daemon handles its own logging via tee)
    su -s /bin/sh app -c "/app/bin/sync-daemon.sh" &
    SYNC_DAEMON_PID=$!
    echo "Sync daemon started with PID $SYNC_DAEMON_PID"
else
    echo "Sync daemon disabled (SYNC_ENABLED=false)"
fi

# Start Nginx in foreground (main process)
echo "Starting Nginx..."
exec nginx -g "daemon off;"
