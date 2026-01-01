# IPTV Organizer Proxy

A powerful Xtream Codes API proxy with advanced filtering, organization, and multi-source management capabilities.

## Features

- **Multi-Source Support**: Manage multiple IPTV sources from a single proxy
- **Advanced Filtering**: YAML-based filtering with include/exclude rules and favoris system
- **Label Extraction**: Automatic label extraction from channel names for enhanced organization
- **Database Support**: MySQL and SQLite supported
- **Adult Content Filtering**: Per-client adult content blocking
- **Background Sync**: Automatic synchronization with upstream sources
- **React Admin Panel**: Modern web-based administration interface
- **REST API**: Complete REST API for programmatic management

## Requirements

- PHP 8.1 or higher
- Composer
- MySQL 8.0+ or SQLite 3
- Node.js 18+ (for admin panel)

## Quick Start

### 1. Install Dependencies

```bash
# Install PHP dependencies
composer install

# Copy environment configuration
cp .env.example.app .env

# Edit .env with your database credentials
nano .env
```

### 2. Database Setup

**For MySQL:**
```bash
# Create database
mysql -u root -p -e "CREATE DATABASE iptv_proxy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Update .env
DB_TYPE=mysql
DB_HOST=localhost
DB_PORT=3306
DB_NAME=iptv_proxy
DB_USER=root
DB_PASS=your_password
```

**For SQLite:**
```bash
# Update .env
DB_TYPE=sqlite
DB_SQLITE_PATH=data/database.sqlite
```

### 3. Run Migrations

```bash
php bin/migrate.php
```

### 4. Start Development Server

```bash
# PHP built-in server
php -S localhost:8080 -t public/

# Or use Docker
docker-compose up -d
```

## Project Structure

```
iptv-organizer-proxy/
├── src/
│   ├── Controllers/    # API controllers
│   ├── Database/       # Database connection and utilities
│   ├── Middleware/     # Authentication, CORS, etc.
│   ├── Models/         # Data models
│   └── Services/       # Business logic
├── config/             # Configuration files
├── migrations/         # Database migrations
├── bin/                # CLI scripts
├── public/             # Web entry point
├── tests/              # Unit and integration tests
├── admin-panel/        # React admin panel (coming in Task 12)
└── composer.json       # PHP dependencies
```

## Environment Variables

See `.env.example.app` for all available configuration options.

### Key Variables:

- `DB_TYPE`: Database type (`mysql` or `sqlite`)
- `APP_ENV`: Environment (`development` or `production`)
- `JWT_SECRET`: Secret key for JWT tokens
- `SYNC_ENABLED`: Enable/disable automatic sync daemon (default: `true`)
- `SYNC_CHECK_INTERVAL`: Daemon check interval in seconds (default: `300`)
- `SYNC_LOCK_TIMEOUT`: Lock timeout for concurrent sync prevention (default: `1800`)
- `DEFAULT_SYNC_INTERVAL`: Default sync interval for new sources in seconds

For detailed sync daemon configuration, see [SYNC-DAEMON.md](SYNC-DAEMON.md)

## Proxy Configuration

The application supports routing all backend HTTP calls through a proxy server. This is useful for:
- Organizations with network policies requiring proxy usage
- Bypassing geographic restrictions
- Adding an extra security layer

### Configuration

Update your `.env` file with proxy settings:

#### Option 1: Using Proxy URL (Recommended)

```bash
PROXY_ENABLED=true
PROXY_URL=http://proxy.example.com:8080
```

With authentication:
```bash
PROXY_URL=http://username:password@proxy.example.com:8080
```

#### Option 2: Using Component-Based Configuration

```bash
PROXY_ENABLED=true
PROXY_TYPE=http
PROXY_HOST=proxy.example.com
PROXY_PORT=8080
PROXY_USERNAME=username
PROXY_PASSWORD=password
```

### Supported Proxy Types

- **HTTP/HTTPS Proxy**: Standard HTTP proxy
  ```bash
  PROXY_URL=http://proxy.example.com:8080
  PROXY_URL=https://secure-proxy.example.com:8443
  ```

- **SOCKS5 Proxy**: For tunneling through SOCKS5 proxies
  ```bash
  PROXY_URL=socks5://proxy.example.com:1080
  PROXY_URL=socks5://user:pass@proxy.example.com:1080
  ```

### What Gets Proxied

When enabled, proxy is applied to:
- Guzzle HTTP client (Xtream API calls to upstream servers)
- cURL requests (stream proxying to clients)
- All backend-to-upstream HTTP communication

**Note**: The admin panel (React frontend) communicates directly with the backend API and does not use the proxy.

## Streaming Architecture

### Memory-Efficient Streaming

The application uses true streaming for binary data (video, streams, etc.) without buffering entire files into memory:

**Stream Proxying Features:**
- **Zero buffering**: Data flows directly from upstream → proxy → client
- **Chunked processing**: Data processed in 8 KB chunks
- **Real-time delivery**: Clients start receiving data immediately (no wait)
- **Memory constant**: ~8 KB memory usage regardless of file size
- **Callback support**: Optional callbacks for monitoring/processing chunks

**Memory comparison for 100 MB video:**
- Before: Would need 100 MB memory buffer
- After: Uses only ~8 KB constant memory

### How It Works

```
Client Request (VLC, KODI, etc.)
    ↓
Proxy StreamDataController
    ↓
HttpClient::streamDirectToClient() ← TRUE STREAMING
    ├─ CURLOPT_HEADERFUNCTION: Process headers as they arrive
    └─ CURLOPT_WRITEFUNCTION: Stream data chunks directly to client
    ↓
Upstream Server Response
    ├─ Chunk 1 (8 KB) → Client (immediately)
    ├─ Chunk 2 (8 KB) → Client (immediately)
    └─ Chunk 3 (8 KB) → Client (immediately)

Memory: ~8 KB constant (one chunk at a time)
```

### Troubleshooting

Check logs for proxy-related messages:
```bash
tail -f logs/app.log | grep -i proxy
```

Common issues:
- **Connection timeout**: Verify proxy server is reachable at `PROXY_HOST:PROXY_PORT`
- **Authentication failed**: Check `PROXY_USERNAME` and `PROXY_PASSWORD`
- **Protocol errors**: Ensure `PROXY_TYPE` matches your proxy server configuration
- **Validation errors**: Check that both username and password are provided together or omitted together

## Development

### Running Tests

```bash
# Run PHPUnit tests
composer test

# Run PHPStan static analysis
composer analyse
```

### Code Style

This project follows PSR-12 coding standards.

## Docker Deployment

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## Database Connection

The application uses a singleton pattern for database connections. Both MySQL and SQLite are supported:

```php
use App\Database\Connection;

$pdo = Connection::getConnection();
```

The connection type is automatically determined from the `DB_TYPE` environment variable.

## API Endpoints

### Xtream Codes API (Client Access)

- `GET /player_api.php` - Authentication endpoint
- `GET /player_api.php?action=get_live_categories` - Live TV categories
- `GET /player_api.php?action=get_live_streams` - Live TV streams
- `GET /player_api.php?action=get_vod_categories` - VOD categories
- `GET /player_api.php?action=get_vod_streams` - VOD streams
- `GET /player_api.php?action=get_series_categories` - Series categories
- `GET /player_api.php?action=get_series` - Series list

### Admin REST API

- `POST /api/login` - Admin authentication
- `GET /api/sources` - List sources
- `GET /api/clients` - List clients
- `GET /api/filters` - List filters
- `POST /api/sources/{id}/sync` - Trigger manual sync

## License

MIT

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting pull requests.

## Support

For issues and questions, please use the GitHub issue tracker.
