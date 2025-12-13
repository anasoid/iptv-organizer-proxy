#!/bin/bash

# Mockoon Control Script
# Usage: ./mockoon/scripts/mockoon.sh [start|stop|restart|status|logs]

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
MOCKOON_DIR="$(dirname "$SCRIPT_DIR")"
CONTAINER_NAME="mockoon-xtream-codes"
COMPOSE_FILE="$MOCKOON_DIR/docker-compose.yml"
COLLECTION_FILE="$MOCKOON_DIR/mockoon-xtream-collection.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
}

print_success() {
    echo -e "${GREEN}✓ ${1}${NC}"
}

print_error() {
    echo -e "${RED}✗ ${1}${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi
    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running"
        exit 1
    fi
}

check_collection_file() {
    if [ ! -f "$COLLECTION_FILE" ]; then
        print_error "Collection file not found: $COLLECTION_FILE"
        exit 1
    fi
}

start_mockoon() {
    print_info "Starting Mockoon mock server..."

    check_docker
    check_collection_file

    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_warning "Container already exists, removing old one..."
        docker rm -f "$CONTAINER_NAME" > /dev/null
    fi

    docker run -d \
        --name "$CONTAINER_NAME" \
        -p 3000:3000 \
        -v "$COLLECTION_FILE:/data/collection.json:ro" \
        mockoon/mockoon:latest \
        --data /data/collection.json --host 0.0.0.0 --port 3000 > /dev/null

    # Wait for server to be ready
    print_info "Waiting for server to be ready..."
    max_attempts=30
    attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s http://localhost:3000/player_api.php?username=testuser&password=testpass > /dev/null 2>&1; then
            print_success "Mockoon server started on http://localhost:3000"
            print_info "Collection: mockoon-xtream-collection.json"
            print_info "Test endpoint: curl 'http://localhost:3000/player_api.php?username=testuser&password=testpass'"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    print_error "Server failed to start after ${max_attempts} seconds"
    docker logs "$CONTAINER_NAME"
    exit 1
}

stop_mockoon() {
    print_info "Stopping Mockoon mock server..."

    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME" > /dev/null
        docker rm "$CONTAINER_NAME" > /dev/null
        print_success "Mockoon server stopped"
    else
        print_warning "Mockoon server is not running"
    fi
}

restart_mockoon() {
    print_info "Restarting Mockoon mock server..."
    stop_mockoon
    sleep 1
    start_mockoon
}

status_mockoon() {
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_success "Mockoon server is running"
        docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Image}}\t{{.Ports}}\t{{.Status}}"
        echo ""
        print_info "Test the mock server:"
        echo "  curl 'http://localhost:3000/player_api.php?username=testuser&password=testpass'"
        echo "  curl 'http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_categories'"
        echo "  curl 'http://localhost:3000/player_api.php?username=testuser&password=testpass&action=get_live_streams'"
    else
        print_warning "Mockoon server is not running"
        print_info "Start it with: $0 start"
    fi
}

logs_mockoon() {
    print_info "Mockoon server logs:"
    docker logs -f "$CONTAINER_NAME"
}

# Main command handling
case "${1:-status}" in
    start)
        start_mockoon
        ;;
    stop)
        stop_mockoon
        ;;
    restart)
        restart_mockoon
        ;;
    status)
        status_mockoon
        ;;
    logs)
        logs_mockoon
        ;;
    *)
        echo "Mockoon Control Script"
        echo ""
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  start       - Start Mockoon mock server in Docker"
        echo "  stop        - Stop Mockoon mock server"
        echo "  restart     - Restart Mockoon mock server"
        echo "  status      - Show Mockoon server status"
        echo "  logs        - Follow Mockoon server logs"
        echo ""
        echo "Examples:"
        echo "  ./mockoon/scripts/mockoon.sh start                           # Start the server"
        echo "  ./mockoon/scripts/mockoon.sh status                          # Check if running"
        echo "  ./mockoon/scripts/mockoon.sh logs                            # View logs"
        echo ""
        exit 1
        ;;
esac
