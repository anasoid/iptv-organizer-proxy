# Task ID: 17

**Title:** Docker Containerization & Deployment Configuration

**Status:** pending

**Dependencies:** 16

**Priority:** high

**Description:** Create production-ready Dockerfiles, Docker Compose configuration, and deployment scripts for backend, frontend, database, and sync worker

**Details:**

1. Create backend Dockerfile: `backend/Dockerfile`
   - Multi-stage build:
     - Stage 1: composer install dependencies
     - Stage 2: production image with PHP-FPM
   - Base image: php:8.2-fpm-alpine
   - Install extensions: pdo, pdo_mysql, pdo_sqlite, curl, json, mbstring, openssl
   - Copy application code to /var/www/html
   - Set proper permissions
   - Expose port 9000
2. Create frontend Dockerfile: `admin-panel/Dockerfile`
   - Multi-stage build:
     - Stage 1: npm build
     - Stage 2: nginx with built assets
   - Base image: node:18-alpine (build), nginx:alpine (production)
   - Copy built files to /usr/share/nginx/html
   - Custom nginx.conf for SPA routing
   - Expose port 80
3. Create Nginx Dockerfile: `nginx/Dockerfile`
   - Base image: nginx:alpine
   - Copy custom nginx.conf
   - Proxy pass to PHP-FPM backend
   - Rewrite rules for Xtream API URLs
   - Static file serving
   - Expose ports 80 and 443
4. Create docker-compose.yml:
   - Services:
     - nginx: reverse proxy for backend and frontend
     - backend: PHP-FPM application
     - frontend: React admin panel (nginx)
     - mysql: MySQL 8.0 database
     - sync-worker: PHP daemon for background sync
   - Networks: app-network
   - Volumes:
     - mysql_data: /var/lib/mysql
     - app_data: /var/www/html/data (for SQLite and logs)
   - Environment variables from .env file
   - Restart policies: always
5. Create docker-compose.sqlite.yml:
   - Same as docker-compose.yml but without MySQL service
   - Backend uses SQLite
6. Create .env.example:
   - All environment variables with descriptions
   - Database configuration (MySQL and SQLite)
   - Application settings
   - Sync configuration
   - JWT secrets
7. Create nginx configuration: `nginx/nginx.conf`
   - Server block for Xtream API (port 80)
   - Proxy pass to backend PHP-FPM
   - Rewrite rules:
     - /player_api.php → backend/public/index.php
     - /live/* → backend/public/index.php
     - /movie/* → backend/public/index.php
     - /series/* → backend/public/index.php
     - /xmltv.php → backend/public/index.php
   - Server block for admin panel (port 3000)
   - Gzip compression
   - Client max body size: 100M
8. Health checks:
   - Backend: PHP-FPM status page
   - Frontend: nginx status
   - MySQL: mysqladmin ping
   - Sync worker: check heartbeat file
9. Docker ignore files:
   - .dockerignore for backend (exclude tests, .git, etc.)
   - .dockerignore for frontend (exclude node_modules, .git)
10. Volume management:
    - Persistent data for MySQL
    - SQLite database file
    - Application logs
11. Create deployment scripts:
    - deploy.sh: builds and starts all containers
    - stop.sh: stops all containers
    - logs.sh: shows logs from all services

**Test Strategy:**

1. Test backend Dockerfile builds successfully
2. Test frontend Dockerfile builds successfully
3. Test docker-compose up starts all services
4. Test backend container serves API correctly
5. Test frontend container serves React app
6. Test nginx proxies requests correctly
7. Test MySQL container initializes database
8. Test sync-worker container runs daemon
9. Test environment variables loaded correctly
10. Test volumes persist data across restarts
11. Test health checks detect service failures
12. Test docker-compose.sqlite.yml works without MySQL
13. Integration test: full stack running in Docker
14. Test deployment scripts work correctly
