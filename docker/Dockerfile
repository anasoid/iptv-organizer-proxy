# Multi-stage build for minimal image size
# Use official Composer image for dependency installation (known stable version)
FROM composer:2.9 AS builder

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
    python3 \
    py3-pip

# Install build dependencies for PHP extensions
RUN apk add --no-cache --virtual .build-deps \
    $PHPIZE_DEPS \
    sqlite-dev \
    gcc \
    musl-dev

# Install PHP extensions
RUN docker-php-ext-install pdo_sqlite && \
    docker-php-ext-enable pdo_sqlite

# Install supervisord (requires python3 and build tools, then cleanup build deps)
RUN pip3 install --no-cache-dir supervisor && \
    apk del .build-deps

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

# Create data and logs directories with proper permissions
RUN mkdir -p /app/data /app/logs && \
    chown -R app:app /app/data /app/logs

# Copy nginx configuration
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/nginx-default.conf /etc/nginx/conf.d/default.conf

# Copy supervisord configuration
COPY docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# Install openssl for generating secrets
RUN apk add --no-cache openssl

# Create .env file with SQLite configuration and auto-generated secrets
RUN JWT_SECRET=$(openssl rand -hex 32) && \
    SESSION_SECRET=$(openssl rand -hex 32) && \
    cat > /app/.env << EOF
DB_TYPE=sqlite
DB_PATH=/app/data/app.sqlite
JWT_SECRET=${JWT_SECRET}
SESSION_SECRET=${SESSION_SECRET}
CORS_ALLOWED_ORIGINS=*
APP_DEBUG=false
EOF

# Declare data directory as volume
VOLUME ["/app/data"]

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
