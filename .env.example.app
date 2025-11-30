# Application Configuration
APP_ENV=development
APP_DEBUG=true
APP_URL=http://localhost:8080

# Database Configuration
# DB_TYPE can be 'mysql' or 'sqlite'
DB_TYPE=mysql

# MySQL Configuration (used when DB_TYPE=mysql)
DB_HOST=localhost
DB_PORT=3306
DB_NAME=iptv_proxy
DB_USER=root
DB_PASS=

# SQLite Configuration (used when DB_TYPE=sqlite)
DB_SQLITE_PATH=data/database.sqlite

# Security
JWT_SECRET=your-secret-key-change-this-in-production
SESSION_SECRET=your-session-secret-change-this

# Sync Configuration
SYNC_ENABLED=true
DEFAULT_SYNC_INTERVAL=3600
SYNC_CHECK_INTERVAL=300

# Logging
LOG_LEVEL=info
LOG_PATH=logs/app.log

# Admin Panel
ADMIN_PANEL_URL=http://localhost:3000

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
