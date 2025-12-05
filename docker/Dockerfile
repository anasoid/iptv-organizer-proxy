# Stage 1: Composer - install PHP dependencies
FROM composer:2.9 AS composer-stage

WORKDIR /app

COPY back/composer.json back/composer.lock ./
RUN composer install --no-dev --optimize-autoloader

# Stage 2: Builder - compile PHP extensions with build tools
FROM php:8.2-fpm-alpine AS builder-stage

RUN apk add --no-cache --no-scripts --force \
    $PHPIZE_DEPS \
    sqlite-dev

RUN docker-php-ext-install pdo_sqlite && \
    docker-php-ext-enable pdo_sqlite

# Stage 3: Runtime - minimal final image with only compiled extensions
FROM php:8.2-fpm-alpine

# Install only runtime dependencies (no build tools)
RUN apk add --no-cache --no-scripts \
    sqlite \
    sqlite-libs \
    nginx && \
    mkdir -p /etc/nginx/conf.d /var/log/nginx

# Copy compiled PHP extensions from builder stage
COPY --from=builder-stage /usr/local/lib/php/extensions /usr/local/lib/php/extensions
COPY --from=builder-stage /usr/local/etc/php/conf.d /usr/local/etc/php/conf.d

# Create app user for security
RUN addgroup -g 1000 app && \
    adduser -D -u 1000 -G app app

WORKDIR /app

# Copy vendor from composer stage
COPY --from=composer-stage --chown=app:app /app/vendor ./vendor

# Copy application code
COPY --chown=app:app back/public ./public
COPY --chown=app:app back/src ./src
COPY --chown=app:app back/config ./config
COPY --chown=app:app back/bin ./bin
COPY --chown=app:app back/migrations ./migrations
COPY --chown=app:app back/bootstrap.php ./

# Copy admin frontend build
COPY --chown=app:app admin/dist ./public/admin

# Create data and logs directories with proper permissions
RUN mkdir -p /app/data /app/logs && \
    chown -R app:app /app/data /app/logs && \
    chmod 777 /app/data /app/logs && \
    chmod -R 755 /app/src /app/config /app/bin /app/migrations /app/public && \
    chmod 644 /app/src/**/*.php /app/config/**/*.php /app/bin/**/*.php /app/migrations/**/*.php /app/public/**/*.php 2>/dev/null || true

# Copy nginx configuration
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/nginx-default.conf /etc/nginx/conf.d/default.conf

# Configure PHP-FPM to run as app user
RUN sed -i 's/user = www-data/user = app/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/group = www-data/group = app/' /usr/local/etc/php-fpm.d/www.conf

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# Install openssl for generating secrets
RUN apk add --no-cache --no-scripts openssl

# Create .env file with SQLite configuration and auto-generated secrets
RUN JWT_SECRET=$(openssl rand -hex 32) && \
    SESSION_SECRET=$(openssl rand -hex 32) && \
    cat > /app/.env << EOF
DB_TYPE=sqlite
DB_SQLITE_PATH=/app/data/database.sqlite
JWT_SECRET=${JWT_SECRET}
SESSION_SECRET=${SESSION_SECRET}
CORS_ALLOWED_ORIGINS=*
APP_DEBUG=false
EOF

# Declare data directory as volume
VOLUME ["/app/data"]

# Expose port
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:9090/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/docker-entrypoint.sh"]
