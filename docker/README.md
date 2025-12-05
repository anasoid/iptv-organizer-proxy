# Docker Configuration

This directory contains all Docker-related configuration files for building and running the IPTV Organizer Proxy application.

## Files

### `Dockerfile`
Multi-stage Docker build configuration that creates a minimal Alpine-based PHP image (~150MB).

- **Builder stage**: Installs Composer dependencies
- **Runtime stage**: Creates the final minimal image with only needed files
- Includes PHP 8.2-FPM, Nginx, and Supervisor
- Runs migrations automatically on startup
- Non-root user for security

### `docker-compose.yml`
Docker Compose configuration for easy local development and testing.

- Single service: `app` (PHP-FPM + Nginx + Supervisor)
- SQLite database with persistent volume
- Configurable via environment variables
- Health checks included
- Volumes:
  - `../data`: SQLite database directory
  - `../logs`: Application logs

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

### `supervisord.conf`
Process manager configuration:
- Manages PHP-FPM process
- Manages Nginx process
- Auto-restart on failure
- Logs to `/app/logs/`

### `docker-entrypoint.sh`
Entrypoint script that runs when the container starts:
- Creates SQLite database directory
- Waits for external database (if configured)
- Runs database migrations
- Sets proper file permissions
- Starts the main process (supervisord)

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
├── supervisord.conf         # Process manager config
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
- **Logs**: All logs written to `/app/logs/`
- **Persistence**: SQLite database persists via volumes

## For More Information

See the main `DOCKER.md` file in the project root for complete deployment guide and instructions.
