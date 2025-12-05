# Docker Quick Start Guide

This guide shows how to run the pre-built Docker image from GitHub Container Registry.

## Prerequisites

- Docker and Docker Compose installed
- GitHub username for the image repository

## Quick Start (Development)

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/iptv-organizer-proxy.git
cd iptv-organizer-proxy
```

### 2. Set up environment variables

```bash
# Copy the example file
cp .env.example .env

# Edit .env with your GitHub username
# GITHUB_USERNAME=yourusername

# Or set environment variables directly
export GITHUB_USERNAME=yourusername
export JWT_SECRET=$(openssl rand -hex 32)
export SESSION_SECRET=$(openssl rand -hex 32)
```

### 3. Run with Docker Compose

```bash
# Start the application
docker-compose up -d

# Check if it's running
docker-compose ps

# View logs
docker-compose logs -f app
```

### 4. Access the application

- **Admin Panel**: http://localhost:8080/admin
- **API**: http://localhost:8080/api
- **Health Check**: http://localhost:8080/health

**Default Credentials:**
- Username: `admin`
- Password: `nimda$123`

## Common Commands

```bash
# Start application
docker-compose up -d

# Stop application
docker-compose down

# View logs
docker-compose logs -f app

# Restart
docker-compose restart

# Remove everything including volumes
docker-compose down -v

# Execute command in container
docker-compose exec app bash

# Check database
docker-compose exec app sqlite3 /app/data/app.sqlite ".tables"
```

## Custom Configuration

### Override environment variables

```bash
# Method 1: Using .env file
echo "JWT_SECRET=$(openssl rand -hex 32)" >> .env
docker-compose up -d

# Method 2: Command line
JWT_SECRET=$(openssl rand -hex 32) \
SESSION_SECRET=$(openssl rand -hex 32) \
docker-compose up -d

# Method 3: Edit docker-compose.yml directly
nano docker-compose.yml  # Edit environment section
```

### Change application port

```bash
# Using environment variable
APP_PORT=9000 docker-compose up -d

# Or edit .env
echo "APP_PORT=9000" >> .env
```

### Enable CORS for specific domain

```bash
CORS_ALLOWED_ORIGINS=https://yourdomain.com docker-compose up -d
```

## Persistent Data

The SQLite database is stored in a Docker volume named `app-data`. This ensures data persists across container restarts:

```bash
# List volumes
docker volume ls | grep app-data

# Inspect volume location
docker volume inspect iptv-organizer-proxy_app-data

# Backup database
docker-compose exec app cp /app/data/app.sqlite /app/data/backup.sqlite
```

## Troubleshooting

### Container won't start

```bash
# Check logs
docker-compose logs app

# Check health
docker-compose exec app wget http://localhost:8080/health

# Verify environment variables
docker-compose exec app env | grep -E "JWT_SECRET|SESSION_SECRET"
```

### Database issues

```bash
# Check if database exists
docker-compose exec app ls -la /app/data/

# Run migrations manually
docker-compose exec app php /app/bin/migrate.php

# Check database schema
docker-compose exec app sqlite3 /app/data/app.sqlite ".schema"
```

### Port already in use

```bash
# Use different port
APP_PORT=9000 docker-compose up -d

# Or find what's using the port
lsof -i :8080
```

## Production Deployment

For production, use the same `docker-compose.yml` but with additional steps:

```bash
# 1. Generate strong secrets
JWT_SECRET=$(openssl rand -hex 32)
SESSION_SECRET=$(openssl rand -hex 32)

# 2. Create production .env
cat > .env.prod << EOF
GITHUB_USERNAME=yourusername
APP_PORT=8080
JWT_SECRET=$JWT_SECRET
SESSION_SECRET=$SESSION_SECRET
CORS_ALLOWED_ORIGINS=https://yourdomain.com
APP_DEBUG=false
EOF

# 3. Run with production config
docker --env-file .env.prod compose up -d
# OR
GITHUB_USERNAME=yourusername \
JWT_SECRET=$JWT_SECRET \
SESSION_SECRET=$SESSION_SECRET \
CORS_ALLOWED_ORIGINS=https://yourdomain.com \
APP_DEBUG=false \
docker-compose up -d

# 4. Setup backup job
# Add to crontab to backup database daily
# 0 2 * * * docker-compose exec app cp /app/data/app.sqlite /backups/app.sqlite.$(date +\%Y\%m\%d)
```

## OpenWRT Deployment

See [DOCKER.md OpenWRT Deployment section](./DOCKER.md#openwrt-deployment) for detailed OpenWRT router instructions.

## Building Locally (Development Only)

If you want to build the image locally instead of pulling from GitHub:

```bash
cd docker
docker-compose -f docker-compose.yml up --build
```

## More Information

- **Full Docker Documentation**: See [DOCKER.md](./DOCKER.md)
- **OpenWRT Setup**: See [DOCKER.md#openwrt-deployment](./DOCKER.md#openwrt-deployment)
- **Kubernetes**: See [DOCKER.md#kubernetes-deployment](./DOCKER.md#kubernetes-deployment)
