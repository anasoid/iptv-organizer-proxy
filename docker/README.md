# Docker Configuration

This directory contains all Docker-related configuration files for building and running the IPTV Organizer Proxy application with a **Quarkus Java backend** and **SQLite database**.

## Overview

The IPTV Organizer Proxy is a full-stack application consisting of:
- **Backend**: Quarkus Java framework (REST API)
- **Frontend**: React admin panel (Vite)
- **Database**: SQLite (single-file database, ideal for deployment)
- **Deployment**: Docker and Docker Compose

## Files

### `Dockerfile`
Multi-stage Docker build for the Quarkus Java application:

- **Stage 1 (Builder)**: Eclipse Temurin JDK 21
  - Compiles Java source code using Gradle
  - Builds optimized Quarkus JAR artifact
  - ~800MB during build (not in final image)

- **Stage 2 (Runtime)**: Eclipse Temurin JRE 21
  - Minimal runtime image with only necessary files (~400MB)
  - Non-root user for security
  - Health checks included
  - Memory optimized for low-resource environments

### `docker-compose.yml`
Docker Compose configuration for local development and testing:

- **Service**: `app` - Quarkus Java backend
  - Builds from `Dockerfile`
  - Exposes port 9090 (configurable via `APP_PORT` env var)
  - SQLite database stored in `/app/data/app.sqlite`
  - Persistent volumes for data and logs
  - Health checks enabled
  - Automatic restart on failure

**Usage:**
```bash
cd docker
docker-compose up --build
```

### `docker-entrypoint.sh`
Startup script that runs when the container starts:

- Creates necessary directories (`/app/data`, `/logs`)
- Configures Quarkus environment variables
- Sets up SQLite database path
- Optimizes JVM memory settings (32MB min, 256MB max)
- Starts the Quarkus application

### `.dockerignore`
Excludes unnecessary files from the Docker build context:
- Git files and IDE configurations
- Node modules and build artifacts
- Old PHP backend source (not needed)
- Environment files
- Development tools

## Quick Start

### Option 1: Using docker-compose (Recommended for local development)

From the `docker/` directory:
```bash
cd docker
docker-compose up --build
```

The application will be available at: `http://localhost:9090`

Access the health endpoint: `http://localhost:9090/health`

View logs:
```bash
docker-compose logs -f app
```

Stop the application:
```bash
docker-compose down
```

### Option 2: Using Docker CLI directly

Build the image:
```bash
docker build -f docker/Dockerfile -t iptv-organizer-proxy:latest .
```

Run the container:
```bash
docker run -d \
  --name iptv-proxy \
  -p 9090:9090 \
  -v iptv-data:/app/data \
  -v iptv-logs:/logs \
  -e JWT_SECRET=$(openssl rand -hex 32) \
  -e SESSION_SECRET=$(openssl rand -hex 32) \
  iptv-organizer-proxy:latest
```

View logs:
```bash
docker logs -f iptv-proxy
```

## Environment Variables

Configure the application by setting environment variables in `docker-compose.yml` or via `-e` flags when running with `docker run`.

### Required
- `JWT_SECRET`: Secret key for JWT token signing (generate with `openssl rand -hex 32`)
- `SESSION_SECRET`: Secret key for session encryption (generate with `openssl rand -hex 32`)

### Database (SQLite)
- `QUARKUS_DATASOURCE_JDBC_URL`: SQLite JDBC URL (default: `jdbc:sqlite:/app/data/app.sqlite`)
- `QUARKUS_DATASOURCE_DB_KIND`: Database type (default: `sqlite`)

### HTTP Server
- `QUARKUS_HTTP_PORT`: Port to listen on (default: `9090`)
- `QUARKUS_HTTP_HOST`: Host to bind to (default: `0.0.0.0`)

### Feature Flags
- `SYNC_ENABLED`: Enable background sync daemon (default: `true`)
- `SYNC_CHECK_INTERVAL`: Sync check interval in seconds (default: `10800` = 3 hours)
- `SYNC_LOCK_TIMEOUT`: Lock timeout for sync operations in seconds (default: `600` = 10 minutes)

### Logging
- `QUARKUS_LOG_LEVEL`: Log level (default: `INFO`; options: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`)
- `QUARKUS_LOG_CONSOLE_FORMAT`: Custom log format string

### CORS
- `CORS_ALLOWED_ORIGINS`: Allowed CORS origins (default: `*`)

### Memory & Performance
- `JAVA_OPTS`: JVM options (default: `-Xms32m -Xmx256m -XX:+UseG1GC`)

### Example .env file for docker-compose
```bash
# Security (REQUIRED - generate these!)
JWT_SECRET=your-secure-random-jwt-secret-here
SESSION_SECRET=your-secure-random-session-secret-here

# Server
APP_PORT=9090
QUARKUS_LOG_LEVEL=INFO

# Features
SYNC_ENABLED=true
SYNC_CHECK_INTERVAL=10800

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

Save this as `.env` in the `docker/` directory and `docker-compose` will automatically load it.

## File Structure

```
docker/
├── Dockerfile                 # Multi-stage build for Quarkus Java app
├── docker-compose.yml         # Local development setup with SQLite
├── docker-entrypoint.sh       # Startup script (JVM configuration)
├── .dockerignore              # Files excluded from build context
└── README.md                  # This file
```

## Database

### SQLite
The application uses **SQLite**, a zero-configuration SQL database:

**Advantages:**
- Single file database (`/app/data/app.sqlite`)
- No separate database server needed
- Perfect for single-instance deployments
- Great for development and testing
- Easy to backup (just copy the file)

**Persistence:**
- Database file is stored in Docker volume `app-data` mapped to `./data`
- Survives container restarts
- Can be backed up by copying `./data/app.sqlite`

**Connection String:**
```
jdbc:sqlite:/app/data/app.sqlite
```

**Migration:**
- Database schema is created automatically on first run
- Future migrations are handled by Quarkus ORM framework

## Key Features

✅ **Multi-stage build**: Optimized for minimal runtime image size  
✅ **Security**: Non-root user, no unnecessary privileges  
✅ **Health checks**: Built-in health endpoint monitoring  
✅ **Easy configuration**: Environment variable based setup  
✅ **Persistent storage**: SQLite data survives container restarts  
✅ **Memory optimized**: G1GC garbage collector for low-memory environments  
✅ **Docker native**: Full Docker and Docker Compose support  
✅ **Logging**: Structured JSON logging to stdout/stderr  

## Performance & Resource Usage

### Build Time
- Cold build (no cache): ~5-10 minutes
- Warm build (with cache): ~1-2 minutes
- Compiled JAR: ~150-200MB

### Runtime Memory
- Minimum heap: 32MB
- Maximum heap: 256MB
- Typical usage: 80-120MB
- Suitable for: Any environment with 256MB+ RAM

### Runtime CPU
- Minimal CPU usage at idle
- Optimized with G1GC for low pause times
- Suitable for: Shared hosting, OpenWRT routers, VPS

### Image Size
- Compressed: ~150-180MB
- Uncompressed: ~400-450MB

## Building for Production

### Standard Docker Build
```bash
docker build -f docker/Dockerfile -t iptv-organizer-proxy:1.0.0 .
```

### With BuildKit (faster, better caching)
```bash
docker buildx build -f docker/Dockerfile -t iptv-organizer-proxy:1.0.0 .
```

### Push to Registry
```bash
docker tag iptv-organizer-proxy:1.0.0 ghcr.io/yourusername/iptv-organizer-proxy:1.0.0
docker push ghcr.io/yourusername/iptv-organizer-proxy:1.0.0
```

## Deployment Examples

### Local Development
```bash
cd docker
docker-compose up --build
# Access at http://localhost:9090
```

### Production (Docker)
```bash
docker pull ghcr.io/yourusername/iptv-organizer-proxy:latest
docker run -d \
  --name iptv-proxy \
  -p 9090:9090 \
  -v /data/iptv:/app/data \
  -v /logs/iptv:/logs \
  -e JWT_SECRET="$(openssl rand -hex 32)" \
  -e SESSION_SECRET="$(openssl rand -hex 32)" \
  --restart unless-stopped \
  ghcr.io/yourusername/iptv-organizer-proxy:latest
```

### Production (Docker Compose)
```bash
# Copy docker-compose.yml to production server
# Create .env file with production secrets
docker-compose up -d
docker-compose logs -f
```

## Troubleshooting

### Health check failing
```bash
# Check logs
docker-compose logs app

# Check if app is running
docker-compose exec app curl http://localhost:9090/health

# Increase start_period if app takes longer to start
# Edit docker-compose.yml: start_period: 30s
```

### Database errors
```bash
# Check database file exists and is readable
docker-compose exec app ls -la /app/data/

# Reset database (CAUTION: data loss!)
docker-compose down -v
docker-compose up --build
```

### Out of memory errors
```bash
# Increase max heap in docker-compose.yml
# Change JAVA_OPTS to: -Xms64m -Xmx512m
```

### Port already in use
```bash
# Use different port in docker-compose.yml
ports:
  - "9091:9090"  # Maps host:9091 to container:9090
```

## For More Information

See the main `DOCKER.md` file in the project root for complete deployment guide and advanced configurations.

