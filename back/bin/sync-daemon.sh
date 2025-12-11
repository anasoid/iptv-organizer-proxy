#!/bin/sh

# Sync Daemon - Shell Script Version
# Lower memory footprint compared to PHP daemon
# Continuously monitors and syncs all active IPTV sources

set -e

# Configuration from environment or defaults
SYNC_CHECK_INTERVAL="${SYNC_CHECK_INTERVAL:-10800}"  # 3 hours
SYNC_LOCK_TIMEOUT="${SYNC_LOCK_TIMEOUT:-600}"       # 10 minutes
LOG_DIR="${LOG_DIR:-/logs/iptv}"
LOG_FILE="${LOG_DIR}/sync-daemon.log"
HEARTBEAT_FILE="/tmp/sync-daemon-heartbeat"

# Ensure log directory exists
mkdir -p "$LOG_DIR"

# Logging functions
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" | tee -a "$LOG_FILE"
}

log_debug() {
    if [ "${VERBOSE:-0}" = "1" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] DEBUG: $*" | tee -a "$LOG_FILE"
    fi
}

# Cleanup function for graceful shutdown
cleanup() {
    log_info "Received termination signal, shutting down gracefully..."
    exit 0
}

# Trap signals for graceful shutdown
trap cleanup TERM INT

# Update heartbeat
update_heartbeat() {
    date +%s > "$HEARTBEAT_FILE"
}

# Get memory usage in MB
get_memory_usage() {
    ps -o rss= -p $$ | awk '{print int($1/1024)}'
}

# Check if a sync lock is valid
is_lock_valid() {
    local lock_file="$1"
    local timeout="$2"

    if [ ! -f "$lock_file" ]; then
        return 1  # No lock exists
    fi

    local lock_time=$(cat "$lock_file" 2>/dev/null || echo "0")
    local current_time=$(date +%s)
    local age=$((current_time - lock_time))

    if [ "$age" -gt "$timeout" ]; then
        log_debug "Lock file $lock_file expired (age: ${age}s), removing..."
        rm -f "$lock_file"
        return 1  # Lock expired
    fi

    return 0  # Lock is valid
}

# Get list of active source IDs using PHP
get_active_sources() {
    php -r "
    require_once '/app/bootstrap.php';
    use App\Models\Source;
    \$sources = Source::getActive();
    foreach (\$sources as \$source) {
        echo \$source->id . '|' . \$source->name . PHP_EOL;
    }
    " 2>/dev/null
}

# Define all task types (always the same 6 tasks)
TASK_TYPES="live_categories live_streams vod_categories vod_streams series_categories series"

# Acquire lock for specific task type
acquire_task_lock() {
    local source_id="$1"
    local task_type="$2"
    local lock_file="/tmp/sync-${source_id}-${task_type}.lock"

    if is_lock_valid "$lock_file" "$SYNC_LOCK_TIMEOUT"; then
        log_debug "Lock exists for source $source_id task $task_type, skipping..."
        return 1  # Lock exists
    fi

    # Create lock file
    date +%s > "$lock_file"
    return 0  # Lock acquired
}

# Release lock for specific task type
release_task_lock() {
    local source_id="$1"
    local task_type="$2"
    local lock_file="/tmp/sync-${source_id}-${task_type}.lock"
    rm -f "$lock_file"
}

# Sync a specific task for a source
sync_task() {
    local source_id="$1"
    local source_name="$2"
    local task_type="$3"

    if ! acquire_task_lock "$source_id" "$task_type"; then
        log_debug "Skipping $source_name/$task_type - already syncing"
        return 0
    fi

    log_info "Starting sync: $source_name/$task_type"

    local sync_start=$(date +%s)
    local task_log="$LOG_DIR/sync-${source_id}-${task_type}.log"

    # Run individual task sync with timeout protection
    if timeout 180 php /app/bin/sync.php --source-id="$source_id" --task-type="$task_type" >> "$task_log" 2>&1; then
        local sync_end=$(date +%s)
        local duration=$((sync_end - sync_start))
        log_info "Completed sync: $source_name/$task_type (${duration}s)"
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            log_error "Timeout: $source_name/$task_type (exceeded 180s)"
        else
            log_error "Failed sync: $source_name/$task_type (exit code: $exit_code)"
        fi
    fi

    release_task_lock "$source_id" "$task_type"
}

# Sync all task types for a source
sync_source() {
    local source_id="$1"
    local source_name="$2"

    log_info "Processing source: $source_name (ID: $source_id)"

    # Sync each of the 6 task types individually
    # The sync.php script will check if each task is due
    for task_type in $TASK_TYPES; do
        sync_task "$source_id" "$source_name" "$task_type"
    done
}

# Main daemon loop
log_info "Starting sync daemon (check interval: ${SYNC_CHECK_INTERVAL}s)"
log_info "Lock timeout: ${SYNC_LOCK_TIMEOUT}s"
log_info "Log directory: $LOG_DIR"

iteration=0

while true; do
    iteration=$((iteration + 1))
    loop_start=$(date +%s)

    # Update heartbeat
    update_heartbeat

    log_debug "Iteration $iteration started"

    # Get active sources
    sources=$(get_active_sources)

    if [ -z "$sources" ]; then
        log_debug "No active sources found, waiting..."
    else
        source_count=$(echo "$sources" | wc -l)
        log_info "Processing $source_count active source(s)"

        # Process each source
        echo "$sources" | while IFS='|' read -r source_id source_name; do
            if [ -n "$source_id" ]; then
                sync_source "$source_id" "$source_name"
            fi
        done
    fi

    # Memory usage check
    memory_usage=$(get_memory_usage)
    log_debug "Memory usage: ${memory_usage}MB"

    # Calculate sleep time
    loop_end=$(date +%s)
    loop_duration=$((loop_end - loop_start))
    sleep_time=$((SYNC_CHECK_INTERVAL - loop_duration))

    if [ "$sleep_time" -lt 0 ]; then
        sleep_time=0
    fi

    log_debug "Iteration $iteration completed in ${loop_duration}s, sleeping for ${sleep_time}s"

    # Sleep until next check
    sleep "$sleep_time"
done
