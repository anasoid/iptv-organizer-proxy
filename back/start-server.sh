#!/bin/bash

# PHP Streaming Proxy Server Startup Script
# Configured for IPTV streaming with proper PHP settings

echo "=========================================="
echo "Starting PHP Streaming Proxy Server"
echo "=========================================="
echo ""
echo "Server: http://localhost:8080"
echo "Configuration: .php-streaming.ini"
echo "Document Root: public/"
echo "Router: router.php"
echo ""
echo "Press Ctrl+C to stop the server"
echo "=========================================="
echo ""

# Change to the back directory if not already there
cd "$(dirname "$0")" || exit 1

# Start PHP built-in server with streaming configuration
php -c .php-streaming.ini -S localhost:8080 -t public router.php
