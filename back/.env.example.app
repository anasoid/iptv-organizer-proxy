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
SYNC_CHECK_INTERVAL=10800
SYNC_LOCK_TIMEOUT=600
# Note: PHP max_execution_time is set to 180s (3 min) in Docker
# Individual sync tasks will timeout after this duration

# Logging
LOG_LEVEL=info
LOG_PATH=logs/app.log

# Admin Panel
ADMIN_PANEL_URL=http://localhost:3000

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080

# Streaming Configuration
# STREAM_USE_REDIRECT: Use 302 redirect for stream requests instead of proxying
#   When true: Returns immediate 302 redirect to upstream stream URL (lowest latency, minimal server load)
#   When false: Proxies stream data through this server (supports stream manipulation/filtering)
#   Default: false
STREAM_USE_REDIRECT=false

# STREAM_FOLLOW_LOCATION: Automatically follow HTTP redirects when proxying streams
#   When true: Follows 302/301 redirects and returns final stream data to client
#   When false: Returns upstream response (302/301) directly to client, client must follow redirect
#   Note: Only used when STREAM_USE_REDIRECT=false (proxying mode)
#   Default: false
STREAM_FOLLOW_LOCATION=false

# XMLTV_USE_REDIRECT: Use 302 redirect for XMLTV/EPG requests instead of proxying
#   When true: Returns immediate 302 redirect to upstream EPG URL
#   When false: Proxies EPG data through this server
#   Default: false
XMLTV_USE_REDIRECT=false

# Proxy Configuration
# Enable/disable proxy for all backend HTTP calls
# When enabled, all HTTP requests (Guzzle and cURL) will route through the configured proxy
# Useful for: organizations with proxy requirements, geographic restrictions, security layers
PROXY_ENABLED=false

# Option 1: Use unified proxy URL (takes precedence over component-based config)
# Examples:
#   http://proxy.example.com:8080
#   http://username:password@proxy.example.com:8080
#   https://secure-proxy.example.com:8443
#   socks5://proxy.example.com:1080
#   socks5://user:pass@proxy.example.com:1080
PROXY_URL=

# Option 2: Use separate component-based configuration (used if PROXY_URL is empty)
# Supported types: http, https, socks5
PROXY_TYPE=http
PROXY_HOST=
PROXY_PORT=
PROXY_USERNAME=
PROXY_PASSWORD=
