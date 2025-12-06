# Stage 1: Composer - install PHP dependencies
FROM composer:2.9 AS composer-stage

WORKDIR /app
COPY back/composer.json back/composer.lock ./
RUN composer install --no-dev --optimize-autoloader && \
    rm -rf /tmp/* /var/tmp/*

# Stage 2: Builder - compile PHP extensions with build tools
FROM php:8.2-fpm-alpine AS builder-stage

RUN apk add --no-cache --no-scripts --force \
    $PHPIZE_DEPS \
    sqlite-dev && \
    docker-php-ext-install pdo_sqlite && \
    docker-php-ext-enable pdo_sqlite && \
    rm -rf /tmp/* /var/tmp/*

# Stage 3: Runtime - minimal final image with only compiled extensions
FROM php:8.2-fpm-alpine

# Install only essential runtime dependencies and create app user
RUN apk add --no-cache --no-scripts \
    sqlite \
    sqlite-libs \
    nginx \
    openssl && \
    mkdir -p /etc/nginx/conf.d /var/log/nginx && \
    addgroup -g 1000 app && \
    adduser -D -u 1000 -G app app && \
    rm -rf /tmp/* /var/tmp/*

# Copy compiled PHP extensions from builder stage (early)
COPY --from=builder-stage /usr/local/lib/php/extensions /usr/local/lib/php/extensions
COPY --from=builder-stage /usr/local/etc/php/conf.d /usr/local/etc/php/conf.d

# Copy vendor from composer stage and set ownership
COPY --from=composer-stage --chown=app:app /app/vendor /app/vendor

WORKDIR /app

# Copy application code (excluded: vendor, tests, data, logs from .dockerignore)
COPY --chown=app:app back/ ./

# Copy admin frontend
COPY --chown=app:app admin/dist ./public/admin

# Copy nginx and entrypoint configuration
COPY --chown=app:app docker/nginx.conf /etc/nginx/nginx.conf
COPY --chown=app:app docker/nginx-default.conf /etc/nginx/conf.d/default.conf
COPY --chown=app:app docker/docker-entrypoint.sh /app/docker-entrypoint.sh

# Configure PHP-FPM, set file permissions, and setup data directory
RUN chmod +x /app/docker-entrypoint.sh && \
    mkdir -p /app/data /tmp/nginx_temp && \
    chown -R app:app /app/data /tmp/nginx_temp && \
    chmod 777 /app/data /tmp/nginx_temp && \
    sed -i 's/user = www-data/user = app/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/group = www-data/group = app/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/^pm = .*/pm = static/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/^pm.max_children = .*/pm.max_children = 2/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/^pm.start_servers = .*/pm.start_servers = 1/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i '/^pm.min_spare_servers/d' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i '/^pm.max_spare_servers/d' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/^pm.max_requests = .*/pm.max_requests = 100/' /usr/local/etc/php-fpm.d/www.conf && \
    sed -i 's/^;access.log = .*/access.log = \/dev\/null/' /usr/local/etc/php-fpm.d/www.conf && \
    echo "output_buffering = 8K" >> /usr/local/etc/php.ini-production && \
    echo "memory_limit = 32M" >> /usr/local/etc/php.ini-production && \
    echo "post_max_size = 25M" >> /usr/local/etc/php.ini-production && \
    echo "upload_max_filesize = 25M" >> /usr/local/etc/php.ini-production && \
    echo "default_socket_timeout = 300" >> /usr/local/etc/php.ini-production && \
    cp /usr/local/etc/php.ini-production /usr/local/etc/php.ini && \
    chmod -R 755 /app/src /app/config /app/bin /app/migrations /app/public && \
    find /app/src -name "*.php" -exec chmod 644 {} \; && \
    find /app/config -name "*.php" -exec chmod 644 {} \; && \
    find /app/bin -name "*.php" -exec chmod 644 {} \; && \
    find /app/migrations -name "*.php" -exec chmod 644 {} \; && \
    find /app/public -name "*.php" -exec chmod 644 {} \; && \
    rm -rf /tmp/* /var/tmp/* ~/.cache

# Declare data directory as volume
VOLUME ["/app/data"]

# Expose port
EXPOSE 9090

# Lightweight health check for OpenWRT (using /dev/tcp instead of wget)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:9090/health || exit 1

# Run entrypoint
ENTRYPOINT ["/app/docker-entrypoint.sh"]
