# Multi-stage build for minimal image size
FROM php:8.2-fpm-alpine as builder

# Install build dependencies
RUN apk add --no-cache \
    composer \
    git \
    curl

WORKDIR /app

# Copy composer files
COPY back/composer.json back/composer.lock ./

# Install PHP dependencies (production only)
RUN composer install --no-dev --optimize-autoloader --classmap-authoritative

# Runtime stage - minimal PHP-FPM image
FROM php:8.2-fpm-alpine

# Install runtime dependencies
RUN apk add --no-cache \
    sqlite \
    sqlite-libs \
    nginx \
    supervisor

# Copy PHP extensions configuration
RUN docker-php-ext-install pdo pdo_sqlite

# Create app user for security
RUN addgroup -g 1000 app && \
    adduser -D -u 1000 -G app app

WORKDIR /app

# Copy vendor from builder
COPY --from=builder --chown=app:app /app/vendor ./vendor

# Copy application code
COPY --chown=app:app back/public ./public
COPY --chown=app:app back/src ./src
COPY --chown=app:app back/config ./config
COPY --chown=app:app back/bin ./bin
COPY --chown=app:app back/migrations ./migrations
COPY --chown=app:app back/bootstrap.php ./

# Create data directory for SQLite with proper permissions
RUN mkdir -p /app/data && chown -R app:app /app/data

# Create logs directory
RUN mkdir -p /app/logs && chown -R app:app /app/logs

# Copy nginx configuration
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/nginx-default.conf /etc/nginx/conf.d/default.conf

# Copy supervisord configuration
COPY docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# Create .env file with SQLite configuration
RUN cat > /app/.env << 'EOF'
DB_TYPE=sqlite
DB_PATH=/app/data/app.sqlite
JWT_SECRET=${JWT_SECRET:-changeme-in-production}
SESSION_SECRET=${SESSION_SECRET:-changeme-in-production}
CORS_ALLOWED_ORIGINS=*
APP_DEBUG=false
EOF

# Set user
USER app

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
