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
