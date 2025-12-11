# Sync Daemon Documentation

## Overview

The sync daemon is a lightweight shell script that continuously monitors and synchronizes active IPTV sources in the background. It's designed to use minimal memory compared to the PHP-based daemon by only loading PHP when needed for actual sync operations.

**Key Feature**: The daemon executes each task type (categories, streams) **individually** instead of syncing all content at once. This ensures each PHP execution stays within the 3-minute timeout and uses minimal memory (< 32MB). See [GRANULAR-SYNC.md](GRANULAR-SYNC.md) for details.

## Features

- **Low Memory Footprint**: Shell script runs with minimal memory overhead
- **Infinite Loop**: Continuously runs and checks for sources to sync
- **Automatic Resynchronization**: All active sources are checked and synced based on their schedules
- **Granular Task Execution**: Each task type (categories, streams) synced individually for optimal resource usage
- **Lock Mechanism**: Prevents concurrent syncs of the same task (per source + task type)
- **Graceful Shutdown**: Handles SIGTERM and SIGINT signals properly
- **Comprehensive Logging**: All operations logged with separate logs per task type

## Configuration

The sync daemon is configured through environment variables:

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SYNC_ENABLED` | `true` | Enable/disable the sync daemon |
| `SYNC_CHECK_INTERVAL` | `10800` (3 hours) | Interval between sync checks (seconds) |
| `SYNC_LOCK_TIMEOUT` | `600` (10 min) | Lock timeout to prevent stuck syncs (seconds) |
| `LOG_DIR` | `/logs/sync-daemon` | Directory for daemon logs |
| `VERBOSE` | `0` | Enable verbose logging (1 = enabled) |

### PHP Execution Timeouts

Individual sync tasks executed by the daemon are subject to PHP execution limits:

| Setting | Value | Description |
|---------|-------|-------------|
| `max_execution_time` | `180s` (3 min) | Maximum time a single sync task can run |
| `default_socket_timeout` | `300s` (5 min) | Timeout for HTTP requests to upstream sources |
| `memory_limit` | `32M` | Maximum memory per PHP process |

These timeouts ensure that:
- Individual sync tasks don't run indefinitely
- Network operations timeout after 5 minutes
- Total sync task completes within 3 minutes or fails
- Lock timeout (600s) is longer than execution timeout to prevent conflicts
- Memory usage stays within 32MB per sync task

**Note**: If your sources require longer sync times, you can rebuild the Docker image with adjusted timeouts in `docker/Dockerfile`.

### Configuration Examples

#### Docker Compose

```yaml
services:
  app:
    environment:
      - SYNC_ENABLED=true
      - SYNC_CHECK_INTERVAL=10800
      - SYNC_LOCK_TIMEOUT=600
```

#### Docker CLI

```bash
docker run -e SYNC_ENABLED=true \
           -e SYNC_CHECK_INTERVAL=10800 \
           -e SYNC_LOCK_TIMEOUT=600 \
           ghcr.io/anasoid/iptv-organizer-proxy:latest
```

#### .env File

```bash
SYNC_ENABLED=true
SYNC_CHECK_INTERVAL=10800
SYNC_LOCK_TIMEOUT=600
```

## Usage

### Docker (Automatic)

When running via Docker, the sync daemon starts automatically if `SYNC_ENABLED=true` (default):

```bash
# Using docker-compose
docker-compose up -d

# Using docker CLI
docker run -d -p 9090:9090 ghcr.io/anasoid/iptv-organizer-proxy:latest
```

### Manual Execution

To run the sync daemon manually:

```bash
# Standard execution
/app/bin/sync-daemon.sh

# With verbose logging
VERBOSE=1 /app/bin/sync-daemon.sh

# With custom check interval (5 minutes)
SYNC_CHECK_INTERVAL=300 /app/bin/sync-daemon.sh
```

### Disabling the Daemon

To disable the sync daemon in Docker:

```bash
# docker-compose
SYNC_ENABLED=false docker-compose up -d

# Or in docker-compose.yml
environment:
  - SYNC_ENABLED=false
```

## How It Works

1. **Startup**: Daemon initializes and creates log directory
2. **Main Loop**: Every `SYNC_CHECK_INTERVAL` seconds:
   - Updates heartbeat file (`/tmp/sync-daemon-heartbeat`)
   - Queries database for active sources
   - For each active source:
     - Iterates through all 6 task types (hardcoded list)
     - For each task type:
       - Attempts to acquire task-specific lock
       - If lock acquired, runs sync via PHP script with `--task-type` argument
       - PHP script checks if task is actually due (exits if not)
       - Logs results to task-specific log file
       - Releases task-specific lock
3. **Graceful Shutdown**: On SIGTERM/SIGINT, completes current iteration and exits

**Optimization**: The daemon doesn't query the database to check which tasks are due. Instead, it always attempts all 6 tasks, and the PHP script (`sync.php`) checks the `sync_schedules` table to determine if each task should actually run. This eliminates unnecessary PHP database calls from the shell script.

### Granular Task Execution

Instead of syncing all content types at once, the daemon executes **each task type separately**:

- `live_categories` - Live TV categories (10-20s, ~15MB)
- `live_streams` - Live TV channels (30-90s, ~25MB)
- `vod_categories` - VOD categories (10-20s, ~15MB)
- `vod_streams` - VOD content (30-120s, ~30MB)
- `series_categories` - Series categories (10-20s, ~15MB)
- `series` - Series episodes (30-90s, ~25MB)

Each task runs in its own PHP process, ensuring:
- ✅ Stays within 3-minute timeout
- ✅ Uses < 32MB memory per task
- ✅ Independent failure isolation
- ✅ Separate logs for debugging

For complete details, see [GRANULAR-SYNC.md](GRANULAR-SYNC.md).

## Lock Mechanism

The sync daemon uses file-based locks to prevent concurrent syncs:

- **Lock File Location**: `/tmp/sync-{source_id}-{task_type}.lock`
- **Lock Content**: Unix timestamp when lock was created
- **Lock Timeout**: Configurable via `SYNC_LOCK_TIMEOUT` (default: 600s / 10 minutes)
- **Lock Expiry**: Locks older than timeout are automatically removed

Example lock files:
```
/tmp/sync-1-live_categories.lock
/tmp/sync-1-live_streams.lock
/tmp/sync-1-vod_streams.lock
```

This ensures:
- No duplicate syncs for the same task type
- Different tasks can run in parallel
- Recovery from crashed sync processes
- Safe concurrent operation with manual syncs
- Granular control per task type

## Logging

### Log Files

| File | Description |
|------|-------------|
| `/logs/sync-daemon.log` | Main daemon log (startup, shutdown, iterations) |
| `/logs/sync-daemon/sync-daemon.log` | Detailed sync operations |
| `/logs/sync-daemon/sync-{source_id}-{task_type}.log` | Per-task sync logs |

Example log files:
```
/logs/sync-daemon/sync-1-live_categories.log
/logs/sync-daemon/sync-1-live_streams.log
/logs/sync-daemon/sync-1-vod_streams.log
```

Benefits:
- Easier to debug specific task failures
- Logs don't get mixed between task types
- Can monitor individual task performance

### Log Levels

- **INFO**: Normal operations, sync start/completion
- **ERROR**: Failed syncs, exceptions
- **DEBUG**: Detailed iteration info (only with `VERBOSE=1`)

### Viewing Logs

```bash
# Main daemon log
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon.log

# Specific task log
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon/sync-1-live_streams.log

# All task logs for a source
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon/sync-1-*.log

# Container logs
docker logs -f iptv-organizer-proxy
```

## Monitoring

### Heartbeat

The daemon updates `/tmp/sync-daemon-heartbeat` every iteration. You can monitor daemon health:

```bash
# Check last heartbeat
docker exec iptv-organizer-proxy cat /tmp/sync-daemon-heartbeat

# Monitor heartbeat changes
docker exec iptv-organizer-proxy sh -c 'watch cat /tmp/sync-daemon-heartbeat'
```

### Process Status

```bash
# Check if daemon is running
docker exec iptv-organizer-proxy ps aux | grep sync-daemon

# View daemon logs
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon.log
```

## Troubleshooting

### Daemon Not Starting

1. Check if disabled: `docker exec iptv-organizer-proxy env | grep SYNC_ENABLED`
2. Check logs: `docker logs iptv-organizer-proxy | grep -i sync`
3. Verify script permissions: `docker exec iptv-organizer-proxy ls -la /app/bin/sync-daemon.sh`

### Syncs Not Running

1. Check active sources: `docker exec iptv-organizer-proxy php /app/bin/list-sources.php`
2. Check sync schedules in database
3. Review daemon logs for errors
4. Check lock files: `docker exec iptv-organizer-proxy ls -la /tmp/sync-*.lock`

### High Memory Usage

The shell script daemon uses minimal memory (~1-2MB). If memory usage is high:

1. Check number of concurrent syncs
2. Review PHP memory limit in `/usr/local/etc/php.ini`
3. Reduce `pm.max_children` in PHP-FPM config
4. Increase `SYNC_CHECK_INTERVAL` to reduce sync frequency

### Stuck Locks

If syncs appear stuck:

1. List locks: `docker exec iptv-organizer-proxy ls -la /tmp/sync-*.lock`
2. Check lock age
3. Manually remove: `docker exec iptv-organizer-proxy rm /tmp/sync-*.lock`
4. Or wait for `SYNC_LOCK_TIMEOUT` for automatic cleanup

## Comparison: Shell vs PHP Daemon

| Feature | Shell Daemon | PHP Daemon (sync-daemon.php) |
|---------|--------------|------------------------------|
| Memory Usage | ~1-2MB | ~30-50MB |
| Startup Time | <1s | ~2-3s |
| Language | Shell (sh) | PHP |
| Dependencies | Minimal | PHP + extensions |
| When to Use | Production, low memory | Development, debugging |

## Best Practices

1. **Set Appropriate Check Interval**: Balance between responsiveness and resource usage
   - High-frequency updates: 1800s (30 min)
   - Normal usage: 10800s (3 hours) - **Default**
   - Low-frequency: 21600s (6 hours)

2. **Monitor Logs**: Regularly check logs for errors or warnings

3. **Lock Timeout**: Set based on longest expected sync duration
   - Small sources: 600s (10 min) - **Default**
   - Large sources: 1200s (20 min)

4. **Resource Limits**: Ensure adequate resources for concurrent syncs

5. **Graceful Restarts**: Use `docker-compose restart` instead of `kill -9`

## Integration with Docker

The sync daemon is automatically integrated into the Docker container via the entrypoint script (`docker-entrypoint.sh`):

1. Container starts
2. Nginx and PHP-FPM start
3. If `SYNC_ENABLED=true`, sync daemon starts as background process
4. Daemon runs under `app:app` user
5. Logs to `/logs/sync-daemon.log`

This ensures the daemon:
- Starts automatically with container
- Restarts with container
- Shares volumes with main application
- Uses same configuration
