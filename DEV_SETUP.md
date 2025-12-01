# Development Setup Guide

This guide covers setting up and running the IPTV Organizer Proxy application locally for development.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Database Setup](#database-setup)
- [Running the Application](#running-the-application)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

- **PHP**: 8.1 or higher (8.3+ recommended)
  ```bash
  php --version
  ```

- **Composer**: PHP dependency manager
  ```bash
  composer --version
  ```

- **Node.js**: 18 or higher
  ```bash
  node --version
  npm --version
  ```

- **MariaDB/MySQL**: Running in Docker
  ```bash
  docker ps | grep mariadb
  ```

### Required PHP Extensions

```bash
# Install required PHP extensions (Ubuntu/Debian)
sudo apt-get update && sudo apt-get install -y \
  php8.3-dom \
  php8.3-xml \
  php8.3-mbstring \
  php8.3-pdo \
  php8.3-mysql \
  php8.3-curl

# Verify installation
php -m | grep -E "dom|xml|pdo|mbstring|curl"
```

---

## Environment Setup

### 1. Clone/Navigate to Project

```bash
cd /path/to/iptv-organizer-proxy
pwd  # Verify you're in the right directory
```

### 2. Install Backend Dependencies

```bash
# Install PHP dependencies
composer install

# Verify installation
ls vendor/
```

### 3. Install Frontend Dependencies

```bash
# Navigate to admin directory
cd admin

# Install Node dependencies
npm install

# Verify installation
ls node_modules/

# Return to project root
cd ..
```

### 4. Configure Environment Files

#### Backend (.env)

```bash
# Copy the example file
cp .env.example.app .env

# Edit with your settings
nano .env  # or use your preferred editor
```

**Required .env settings:**

```bash
# Application
APP_ENV=development
APP_DEBUG=true
APP_URL=http://localhost:8080

# Database (MariaDB in Docker)
DB_TYPE=mysql
DB_HOST=127.0.0.1        # Important: Use IP not 'localhost'
DB_PORT=3306
DB_NAME=iptv_proxy
DB_USER=root
DB_PASS=pass

# Security
JWT_SECRET=dev-secret-key-change-in-production
SESSION_SECRET=dev-session-secret

# Admin Panel
ADMIN_PANEL_URL=http://localhost:5173

# CORS (includes Vite dev server)
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000,http://localhost:8080

# Optional Logging
LOG_LEVEL=debug
LOG_PATH=logs/app.log
```

#### Frontend (admin/.env)

```bash
cd admin

# Copy the example file
cp .env.example .env

# Verify content
cat .env
```

**Expected content:**

```bash
VITE_API_BASE_URL=http://localhost:8080
```

```bash
cd ..  # Return to project root
```

---

## Database Setup

### 1. Start MariaDB (if not running)

```bash
# Check if running
docker ps | grep mariadb

# If not running, start it
docker run -d \
  --name mariadb \
  -e MYSQL_ROOT_PASSWORD=pass \
  -p 3306:3306 \
  mariadb:11.4
```

### 2. Create Database

```bash
# Create the database
docker exec mariadb mariadb -u root -ppass -e \
  "CREATE DATABASE IF NOT EXISTS iptv_proxy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Verify
docker exec mariadb mariadb -u root -ppass -e "SHOW DATABASES LIKE 'iptv_proxy';"
```

### 3. Run Migrations

```bash
# Run all pending migrations
php bin/migrate.php

# Expected output:
# ✓ Migrations tracking table created
# ✓ Migration executed: 001_create_admin_users
# ✓ Migration executed: 002_create_sources
# ... (more migrations)
# ✓ All migrations completed successfully!
```

### 4. Verify Database

```bash
# Check all tables were created
docker exec mariadb mariadb -u root -ppass iptv_proxy -e "SHOW TABLES;"

# Should show:
# admin_users, categories, clients, connection_logs, filters,
# live_streams, migrations, series, sources, sync_logs, vod_streams
```

---

## Running the Application

### Option 1: Two Terminal Windows (Recommended)

#### Terminal 1 - Backend Server

```bash
# Navigate to project root
cd /path/to/iptv-organizer-proxy

# Verify .env
cat .env | grep -E "DB_|APP_"

# Start PHP development server
php -S localhost:8080 -t public/

# You should see:
# [Mon Dec 01 20:30:00 2025] PHP 8.3.6 Development Server
# [Mon Dec 01 20:30:00 2025] Listening on http://localhost:8080
```

#### Terminal 2 - Frontend Server

```bash
# Navigate to admin directory
cd /path/to/iptv-organizer-proxy/admin

# Start Vite development server
npm run dev

# You should see:
# ➜  Local:   http://localhost:5173/
# ➜  press h to show help
```

### Option 2: Single Terminal (Background Process)

```bash
# Backend in background
php -S localhost:8080 -t public/ > /tmp/backend.log 2>&1 &
BACKEND_PID=$!

# Frontend in another way or separate terminal
cd admin && npm run dev
```

---

## Application URLs

| Service | URL | Purpose |
|---------|-----|---------|
| **Backend API** | http://localhost:8080 | Xtream API endpoints |
| **Health Check** | http://localhost:8080/health | API status |
| **Admin Panel** | http://localhost:5173 | React dashboard |
| **Database** | 127.0.0.1:3306 | MariaDB |

---

## Testing

### 1. Backend API Testing

#### Health Check

```bash
# Test API is responding
curl http://localhost:8080/health

# Expected response:
# {"status":"ok","timestamp":1701453000}
```

#### Player API (Xtream Codes)

```bash
# Authenticate endpoint (no credentials needed for now)
curl "http://localhost:8080/player_api.php?username=test&password=test"

# Note: Will fail without valid client credentials
# This is expected at this stage
```

#### API Status

```bash
# Check if backend is running
curl -i http://localhost:8080/health

# Should return HTTP 200 with JSON response
```

### 2. Frontend Testing

#### Browser Check

```bash
# Open in browser
http://localhost:5173

# You should see:
# - IPTV Organizer login page
# - Material-UI themed interface
# - Login form (username/password fields)
```

#### Console Testing

```bash
# Check browser console for errors (F12 -> Console)
# Should show no critical errors
# May show development warnings (normal)
```

### 3. Database Testing

```bash
# Check admin_users table
docker exec mariadb mariadb -u root -ppass iptv_proxy -e \
  "SELECT * FROM admin_users;"

# Should be empty initially (no users created yet)
```

### 4. Running Tests

#### Backend Tests

```bash
# Run PHPUnit tests
composer test

# Run PHPStan analysis
composer analyse

# Both should pass without errors
```

#### Frontend Tests

```bash
cd admin

# Run ESLint
npm run lint

# Run TypeScript check
npm run type-check

# Build check
npm run build

cd ..
```

---

## Creating Test Data

### Create Admin User

```bash
# Interactive creation
php -r "
require 'vendor/autoload.php';
require 'bootstrap.php';
use App\Models\AdminUser;
try {
    \$admin = AdminUser::create('admin', 'password123', 'admin@localhost');
    echo '✓ Admin user created!' . PHP_EOL;
    echo '  Username: admin' . PHP_EOL;
    echo '  Password: password123' . PHP_EOL;
} catch (\Exception \$e) {
    echo '✗ Error: ' . \$e->getMessage() . PHP_EOL;
}
"
```

### Login to Admin Panel

1. Open http://localhost:5173
2. Enter credentials:
   - Username: `admin`
   - Password: `password123`
3. Click "Sign In"

---

## Development Workflow

### 1. Start Services

```bash
# Terminal 1: Backend
php -S localhost:8080 -t public/

# Terminal 2: Frontend
cd admin && npm run dev
```

### 2. Make Changes

```bash
# Backend changes auto-reload (restart server if needed)
# Frontend changes hot-reload automatically

# View logs
tail -f /tmp/backend.log  # If running in background
```

### 3. Verify Changes

```bash
# Run tests
composer test           # Backend
npm run lint           # Frontend

# Check code quality
composer analyse       # PHPStan
npm run type-check    # TypeScript
```

### 4. Debug Issues

#### Backend Debugging

```bash
# Check logs
tail -f logs/app.log

# PHP error log
php -i | grep "error_log"

# Enable verbose error reporting in .env
APP_DEBUG=true
```

#### Frontend Debugging

```bash
# Browser DevTools (F12)
# - Console tab for errors
# - Network tab for API calls
# - Application tab for localStorage

# Vite debug mode (add to admin/vite.config.ts)
export default {
  define: {
    'import.meta.env.DEV': true
  }
}
```

---

## Stopping Services

```bash
# Backend: Press Ctrl+C in backend terminal

# Frontend: Press Ctrl+C in frontend terminal

# MariaDB: Keep running or stop with:
# docker stop mariadb
```

---

## Troubleshooting

### Port Already in Use

```bash
# Backend port 8080 in use
lsof -i :8080  # Find process
php -S localhost:8000 -t public/  # Use different port

# Frontend port 5173 in use
lsof -i :5173  # Find process
# Update admin/.env if using different port
```

### Database Connection Failed

```bash
# Check MariaDB is running
docker ps | grep mariadb

# Verify connection
docker exec mariadb mariadb -u root -ppass -e "SELECT 1;"

# Check .env has 127.0.0.1 not localhost
grep DB_HOST .env

# Verify database exists
docker exec mariadb mariadb -u root -ppass -e "SHOW DATABASES;"
```

### CORS Errors

```bash
# Check CORS settings in .env
grep CORS .env

# Should include:
# CORS_ALLOWED_ORIGINS=http://localhost:5173,...

# Verify backend is responding
curl -H "Origin: http://localhost:5173" http://localhost:8080/health
```

### Frontend Can't Connect to API

```bash
# Verify backend is running
curl http://localhost:8080/health

# Check admin/.env
cat admin/.env

# Check browser console (F12) for CORS/network errors

# Ensure API base URL is correct
VITE_API_BASE_URL=http://localhost:8080
```

### PHP Extensions Missing

```bash
# Check installed extensions
php -m

# Install missing ones
# Ubuntu/Debian:
sudo apt-get install php8.3-{extension-name}

# Restart PHP server
# Stop: Ctrl+C
# Start: php -S localhost:8080 -t public/
```

### Composer Issues

```bash
# Update composer
composer self-update

# Clear cache
composer clear-cache

# Reinstall dependencies
rm composer.lock
composer install
```

---

## Performance Optimization

### Backend

```bash
# Production build (optional)
composer install --no-dev

# Enable OPcache
php -i | grep opcache

# Profile code if needed
php -d xdebug.mode=profile ...
```

### Frontend

```bash
# Check bundle size
npm run build

# Analyze build
npm install --save-dev rollup-plugin-visualizer
```

---

## Useful Commands Reference

### Backend

```bash
# Start server
php -S localhost:8080 -t public/

# Run tests
composer test

# Static analysis
composer analyse

# Create admin user
php -r "require 'vendor/autoload.php'; require 'bootstrap.php'; \
  use App\Models\AdminUser; AdminUser::create('user', 'pass');"

# Run migrations
php bin/migrate.php

# Sync all sources (background)
php bin/sync-all-sources.php
```

### Frontend

```bash
# Start dev server
npm run dev

# Run linter
npm run lint

# Type check
npm run type-check

# Build
npm run build

# Preview build
npm run preview
```

### Database

```bash
# Connect to database
docker exec -it mariadb mariadb -u root -ppass iptv_proxy

# Show tables
docker exec mariadb mariadb -u root -ppass iptv_proxy -e "SHOW TABLES;"

# Check migrations
docker exec mariadb mariadb -u root -ppass iptv_proxy -e "SELECT * FROM migrations;"
```

---

## Common Development Tasks

### Add New PHP Dependency

```bash
# Install package
composer require vendor/package

# Add to code
use Vendor\Package\Class;

# Version control
git add composer.json composer.lock
```

### Add New NPM Package

```bash
cd admin

# Install package
npm install package-name

# Use in code
import { Component } from 'package-name';

# Version control
git add package.json package-lock.json
```

### Create New API Endpoint

1. Create controller in `src/Controllers/`
2. Add route in `public/index.php`
3. Test with `curl`
4. Update admin panel if needed

### Create New React Component

1. Create file in `admin/src/components/`
2. Export component
3. Import in page
4. Test in browser

---

## Next Steps

1. ✅ Complete environment setup (this guide)
2. ✅ Start backend and frontend servers
3. ✅ Create admin user and log in
4. 📋 Create IPTV source
5. 👥 Create clients
6. 🎬 Manage streams and categories
7. 🔧 Configure filters

---

## Getting Help

- Check logs: `logs/app.log` and browser console (F12)
- Run tests: `composer test` and `npm run lint`
- Check syntax: `php -l file.php` and `npm run type-check`
- Common issues: See [Troubleshooting](#troubleshooting) section above

---

## Support

For issues and questions:
- Check the main [README.md](README.md)
- Review code comments and docstrings
- Check GitHub issues
- Review database schema in [MODELS.md](MODELS.md)

Happy coding! 🚀
