# Docker Configuration

This directory contains all Docker-related configuration files for building and running the IPTV Organizer Proxy application.

## Files

### `Dockerfile`
Multi-stage Docker build configuration that creates a minimal Alpine-based PHP image (~150MB).

- **Builder stage**: Installs Composer dependencies
- **Runtime stage**: Creates the final minimal image with only needed files
- Includes PHP 8.2-FPM and Nginx
- Runs migrations automatically on startup
- Non-root user for security

### `docker-compose.yml`
Docker Compose configuration for easy local development and testing.

- Single service: `app` (PHP-FPM + Nginx)
- SQLite database with persistent volume
- Configurable via environment variables
- Health checks included
- Volumes:
  - `../data`: SQLite database directory

**Usage:**
```bash
cd docker
docker-compose up --build
```

### `nginx.conf`
Main Nginx configuration with global settings:
- Worker processes auto-detection
- Gzip compression
- Log formatting
- Includes site-specific config from `conf.d/`

### `nginx-default.conf`
Nginx site configuration for the application:
- Listens on port 8080
- Security headers (X-Frame-Options, X-XSS-Protection, etc.)
- Blocks access to sensitive files (`.env`, `composer.json`, etc.)
- Admin SPA routing (rewrites `/admin/*` to `index.html`)
- Static asset caching (1 year)
- PHP-FPM backend routing
- API request routing to `index.php`

### `docker-entrypoint.sh`
Entrypoint script that runs when the container starts:
- Creates SQLite database directory
- Waits for external database (if configured)
- Runs database migrations
- Sets proper file permissions
- Starts PHP-FPM and Nginx

### `.dockerignore`
Files and directories to exclude from the Docker build context:
- Source control files (`.git`, `.github`)
- Node modules and build artifacts
- Local `.env` files
- Test files and temporary files
- Development dependencies

## Quick Start

From project root:
```bash
# Option 1: Using docker-compose
cd docker
docker-compose up --build

# Option 2: Using docker CLI
docker build -f docker/Dockerfile -t iptv-organizer-proxy .
docker run -d -p 8080:8080 -e JWT_SECRET=your-secret iptv-organizer-proxy
```

## Environment Variables

Set these in `docker-compose.yml` or when running with `docker run -e`:

```bash
DB_TYPE=sqlite                           # Database type (SQLite only in Docker)
DB_PATH=/app/data/app.sqlite             # SQLite database file path
JWT_SECRET=your-secure-jwt-secret        # REQUIRED - JWT signing secret
SESSION_SECRET=your-session-secret       # Session encryption secret
CORS_ALLOWED_ORIGINS=*                   # CORS allowed origins
APP_DEBUG=false                          # Debug mode (false in production)
```

## File Structure

```
docker/
├── Dockerfile                # Multi-stage Docker image definition
├── docker-compose.yml       # Local development setup
├── nginx.conf               # Nginx main config
├── nginx-default.conf       # Nginx site config
├── docker-entrypoint.sh     # Startup script
├── .dockerignore             # Exclude from build context
└── README.md                 # This file
```

## Key Features

- **Minimal Size**: ~150MB image, ~50-100MB running container
- **Security**: Non-root user, denied access to sensitive directories
- **Auto-migrations**: Database schema created automatically
- **Health Checks**: Built-in health endpoint monitoring
- **Easy Configuration**: Environment variable based configuration
- **Logs**: Docker captures all logs (access via `docker logs`)
- **Persistence**: SQLite database persists via volumes

## VFS Optimization for OpenWRT

The Dockerfile has been optimized for OpenWRT and low-memory environments:

### Build
```bash
docker build -f docker/Dockerfile -t iptv-organizer-proxy .
# Image size: ~180-200MB compressed
# Includes: Admin UI, API, all features
# Optimized for: Servers, desktops, and OpenWRT routers (256MB+ RAM)
```

### Optimizations Included

1. **PHP-FPM `ondemand` mode** - spawns processes only when needed:
   - max_children: 16 (vs 25 default)
   - start_servers: 2 (vs 8 default)
   - min_spare_servers: 1 (vs 5 default)
   - max_spare_servers: 4 (vs 35 default)
   - max_requests: 500 (restart after 500 requests to prevent leaks)

2. **Aggressive cache cleanup** - removes /tmp, /var/tmp, ~/.cache after each stage
3. **Combined RUN commands** - reduced Docker layer count
4. **Minimal dependencies** - only essential Alpine packages (sqlite, nginx, openssl)
5. **Logs to stdout** - Docker captures automatically (no disk I/O overhead)

### Deployment on OpenWRT

```bash
# On OpenWRT router (SSH into router)
ssh root@192.168.1.1

# Create persistent storage
mkdir -p /mnt/sda1/iptv-data

# Pull and run image
docker pull ghcr.io/yourusername/iptv-organizer-proxy:latest

docker run -d \
  --name iptv-proxy \
  -p 8080:8080 \
  -v /mnt/sda1/iptv-data:/app/data \
  -e JWT_SECRET=$(openssl rand -hex 32) \
  -e SESSION_SECRET=$(openssl rand -hex 32) \
  ghcr.io/yourusername/iptv-organizer-proxy:latest

# View logs
docker logs -f iptv-proxy
```

Access admin panel: `http://192.168.1.1:8080/admin`

### Memory Usage

- **Runtime image**: ~50-100MB RAM (ondemand PHP-FPM mode)
- **Compressed image**: ~180-200MB
- **Suitable for**: Routers with 256MB+ RAM and 512MB+ storage

## For More Information

See the main `DOCKER.md` file in the project root for complete deployment guide and instructions.
