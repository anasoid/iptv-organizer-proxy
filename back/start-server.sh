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

# Create logs directory if it doesn't exist
mkdir -p logs

# Set log file path
LOG_FILE="logs/server.log"

echo "Logging to: $LOG_FILE"
echo ""

# Start PHP built-in server with streaming configuration and log output to file
php -c .php-streaming.ini -S localhost:8080 -t public router.php 2>&1 | tee "$LOG_FILE"
