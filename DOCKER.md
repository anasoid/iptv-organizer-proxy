# Docker Deployment Guide

This project can be deployed using Docker with a minimal Alpine-based image optimized for low memory usage and small disk footprint.

## Features

- **Minimal Image Size**: ~150MB (Alpine-based PHP 8.2-FPM)
- **Low Memory Usage**: ~50-100MB per container
- **SQLite Database**: Persistent volume for data
- **Zero Configuration**: Pre-configured with sensible defaults
- **Health Checks**: Built-in health monitoring
- **Auto Migrations**: Runs database migrations on startup
- **Security**: Non-root user, denied access to sensitive directories

## Quick Start

### Using Docker Compose (Recommended for Local Development)

```bash
# Clone the repository
git clone https://github.com/yourusername/iptv-organizer-proxy.git
cd iptv-organizer-proxy/docker

# Build and start the container
docker-compose up --build

# The application will be available at http://localhost:8080
# Admin panel at http://localhost:8080/admin
```

### Using Docker CLI

```bash
# Build the image from project root
docker build -f docker/Dockerfile -t iptv-organizer-proxy .

# Run the container
docker run -d \
  -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -e JWT_SECRET=your-secure-jwt-secret \
  -e SESSION_SECRET=your-secure-session-secret \
  --name iptv-proxy \
  iptv-organizer-proxy
```

## Configuration

### Environment Variables

```bash
# Database
DB_TYPE=sqlite              # Only sqlite is included in the Docker image
DB_PATH=/app/data/app.sqlite

# JWT Configuration
JWT_SECRET=your-secret-key  # REQUIRED - Set in production
SESSION_SECRET=your-session-key

# CORS
CORS_ALLOWED_ORIGINS=*      # Change to your domain in production

# Debug Mode
APP_DEBUG=false             # Set to false in production
```

### With Custom Environment

```bash
# From docker directory, create .env file
cd docker
cat > .env << EOF
JWT_SECRET=your-production-jwt-secret
SESSION_SECRET=your-production-session-secret
CORS_ALLOWED_ORIGINS=https://yourdomain.com
APP_DEBUG=false
EOF

# Run with custom environment
docker-compose --env-file .env up

# Or from project root
docker-compose -f docker/docker-compose.yml --env-file docker/.env up
```

## Volumes

The container uses a volume for persistent data:

```yaml
volumes:
  data:  /app/data        # SQLite database file
```

Application logs are written to stderr/stdout and captured by Docker. Use `docker logs` to view them.

## Default Credentials

After the first run, you can login with:
- Username: `admin`
- Password: `nimda$123`

**IMPORTANT**: Change the default password in production!

## Building for Production

### GitHub Container Registry

The CI/CD pipeline automatically builds and pushes images to GitHub Container Registry:

```bash
# Pull the latest image
docker pull ghcr.io/yourusername/iptv-organizer-proxy:latest

# Run in production
docker run -d \
  -p 8080:8080 \
  -v data:/app/data \
  -e JWT_SECRET=$(openssl rand -hex 32) \
  -e SESSION_SECRET=$(openssl rand -hex 32) \
  -e CORS_ALLOWED_ORIGINS=https://yourdomain.com \
  -e APP_DEBUG=false \
  ghcr.io/yourusername/iptv-organizer-proxy:latest
```

## Health Check

The container includes a built-in health check:

```bash
# Check container health
docker ps | grep iptv-proxy

# Expected output shows: (healthy)
```

## Logs

View application logs:

```bash
# Using Docker Compose
docker-compose logs -f app

# Using Docker CLI
docker logs -f iptv-proxy
```

## Troubleshooting

### Container exits immediately

Check logs:
```bash
docker logs iptv-proxy
```

Common issues:
- Missing `JWT_SECRET` environment variable
- Permission issues with volumes
- Database initialization failure

### Migrations fail

The container runs migrations automatically on startup. Check logs:
```bash
docker logs iptv-proxy | grep -i migrat
```

### High memory usage

The Alpine-based PHP image is optimized for low memory usage. If you still experience high memory usage:
- Reduce `worker_processes` in nginx.conf
- Reduce `max_children` in PHP-FPM config

### SQLite database locked

This can happen with concurrent access. Solutions:
- Ensure only one container instance is accessing the database
- Use MySQL/PostgreSQL for production with multiple instances

## Production Deployment

For production environments:

1. **Generate secure secrets**:
   ```bash
   JWT_SECRET=$(openssl rand -hex 32)
   SESSION_SECRET=$(openssl rand -hex 32)
   ```

2. **Use environment file** (don't hardcode secrets):
   ```bash
   docker run -d \
     --env-file production.env \
     -v prod-data:/app/data \
     ghcr.io/yourusername/iptv-organizer-proxy:latest
   ```

3. **Set up reverse proxy** (Nginx, Traefik, etc):
   - Enable HTTPS/TLS
   - Set `CORS_ALLOWED_ORIGINS` to your domain
   - Add security headers

4. **Monitor logs**:
   - Set up log collection (ELK stack, CloudWatch, etc.)
   - Monitor health check endpoint

5. **Backup volumes**:
   - Regular backups of `/app/data` volume
   - Consider external database for multi-instance setup

## Kubernetes Deployment

For Kubernetes deployments, use the GitHub Container Registry image with a Deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: iptv-proxy
spec:
  replicas: 1  # Use 1 for SQLite (increase with external DB)
  selector:
    matchLabels:
      app: iptv-proxy
  template:
    metadata:
      labels:
        app: iptv-proxy
    spec:
      containers:
      - name: app
        image: ghcr.io/yourusername/iptv-organizer-proxy:latest
        ports:
        - containerPort: 8080
        env:
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: jwt-secret
        - name: SESSION_SECRET
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: session-secret
        - name: CORS_ALLOWED_ORIGINS
          value: "https://yourdomain.com"
        - name: APP_DEBUG
          value: "false"
        volumeMounts:
        - name: data
          mountPath: /app/data
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 30
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: app-data
```

## Image Sizes

Approximate sizes:
- Base Alpine PHP: ~150MB
- With dependencies: ~180-200MB (compressed)
- With admin build: ~200-220MB (compressed)

## Performance Tuning

The Docker image includes optimizations for:
- Gzip compression for text/JSON responses
- Browser caching for assets (1 year)
- Efficient PHP-FPM configuration
- Nginx optimizations for low memory usage

For additional tuning:
- Adjust `worker_processes` in nginx.conf
- Adjust PHP-FPM `pm.max_children` based on available memory
- Use CDN for static assets in production

## OpenWRT Deployment

The Docker image is built for multiple architectures including ARM support for OpenWRT routers.

### Supported Architectures

- **linux/amd64** - x86-64 servers and desktops
- **linux/arm/v7** - 32-bit ARM (most common OpenWRT routers: TP-Link, ASUS, Linksys, etc.)
- **linux/arm64** - 64-bit ARM (newer routers: Raspberry Pi 4+, newer high-end routers)
- **linux/386** - 32-bit x86 legacy systems

### Quick Start on OpenWRT

1. **Determine your router's architecture**:
   ```bash
   # SSH into your OpenWRT router
   ssh root@192.168.1.1

   # Check architecture
   uname -m
   # Output: armv7l (32-bit ARM) or aarch64 (64-bit ARM)
   ```

2. **Pull and run the image**:
   ```bash
   # Replace with your architecture if needed
   docker pull ghcr.io/yourusername/iptv-organizer-proxy:latest

   docker run -d \
     --name iptv-proxy \
     -p 8080:8080 \
     -e JWT_SECRET=$(openssl rand -hex 32) \
     -e SESSION_SECRET=$(openssl rand -hex 32) \
     -e CORS_ALLOWED_ORIGINS=* \
     -e APP_DEBUG=false \
     ghcr.io/yourusername/iptv-organizer-proxy:latest
   ```

3. **Persist data across reboots**:
   ```bash
   # Create persistent storage
   mkdir -p /mnt/sda1/iptv-data

   docker run -d \
     --name iptv-proxy \
     -p 8080:8080 \
     -v /mnt/sda1/iptv-data:/app/data \
     -e JWT_SECRET=$(openssl rand -hex 32) \
     -e SESSION_SECRET=$(openssl rand -hex 32) \
     ghcr.io/yourusername/iptv-organizer-proxy:latest
   ```

### OpenWRT Resources

- **Docker on OpenWRT**: https://openwrt.org/packages/pkgdata/docker
- **OpenWrt Wiki**: https://openwrt.org/
- **Router Architecture Guide**: Check your router model on OpenWrt Table of Hardware

### Performance Notes for OpenWRT

- **Memory**: The image runs on ~50-100MB RAM, suitable for most routers
- **Storage**: Minimal footprint (~150MB compressed image)
- **CPU**: Optimized for ARM processors
- **Network**: Configure port forwarding in OpenWrt to expose admin panel if needed

### Accessing the Admin Panel on OpenWRT

If running on a router at `192.168.1.1`:
```
http://192.168.1.1:8080/admin
```

### Backing Up Data on OpenWRT

```bash
# Backup SQLite database
docker exec iptv-proxy cp /app/data/app.sqlite /mnt/sda1/backup/app.sqlite.backup

# Or mount backup location
docker run -d \
  -v /mnt/sda1/iptv-data:/app/data \
  -v /mnt/sda1/backup:/backup \
  ... # rest of options
```

## Support

For issues with Docker deployment:
1. Check the logs: `docker logs iptv-proxy`
2. Verify environment variables: `docker inspect iptv-proxy`
3. Test connectivity: `docker exec iptv-proxy wget http://localhost:8080/health`
4. Check volumes: `docker volume inspect <volume-name>`
5. For OpenWRT-specific issues: Check available disk space with `df -h`
