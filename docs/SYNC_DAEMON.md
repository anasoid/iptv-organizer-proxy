# Background Sync Daemon

## Overview

The Background Sync Daemon (`bin/sync-daemon.php`) is a long-running PHP process that automatically synchronizes IPTV sources with upstream servers on a scheduled basis. It supports all 6 sync task types per source and runs independently of web requests.

## Features

- **Independent Task Scheduling**: Each of the 6 sync task types (live_categories, live_streams, vod_categories, vod_streams, series_categories, series) can have independent sync schedules per source
- **Lock Mechanism**: Per-task-type locks prevent duplicate simultaneous syncs
- **Graceful Shutdown**: Handles SIGTERM and SIGINT signals for clean shutdown
- **Comprehensive Logging**: Monolog-based logging with daily rotation to both file and stdout
- **Memory Management**: Monitors memory usage and restarts when threshold exceeded
- **Health Checks**: Heartbeat file for monitoring daemon health
- **Error Handling**: Individual task failures don't stop the daemon

## Installation

### Prerequisites

- PHP 8.1+
- Monolog library (included in composer.json)
- PCNTL extension (for signal handling, optional but recommended)

### Database Setup

The daemon requires the `sync_schedule` table to track per-task sync scheduling:

```bash
php bin/migrate.php
```

This creates the `sync_schedule` table which stores:
- `source_id`: ID of the IPTV source
- `task_type`: One of 6 sync types
- `next_sync`: When this specific task should run next
- `last_sync`: When this task last completed
- `sync_interval`: Interval between syncs in seconds

## Usage

### Start the Daemon

```bash
php bin/sync-daemon.php
```

### Options

- `--help`: Display help message
- `--dry-run`: Run one iteration without actual syncs (useful for testing)
- `--verbose`: Show detailed debug output

### Examples

```bash
# Start daemon with default settings
php bin/sync-daemon.php

# Test mode - run one iteration
php bin/sync-daemon.php --dry-run

# Verbose logging
php bin/sync-daemon.php --verbose

# Show help
php bin/sync-daemon.php --help
```

## Configuration

Configure via environment variables (in `.env` file):

```env
# Check interval in seconds (default: 300)
SYNC_CHECK_INTERVAL=300

# Lock file timeout in seconds (default: 1800 - 30 minutes)
SYNC_LOCK_TIMEOUT=1800

# Memory limit in MB before restart (default: 256)
SYNC_MEMORY_LIMIT=256

# Log directory (default: logs/sync-daemon)
LOG_DIR=logs/sync-daemon
```

## How It Works

### Main Loop

1. **Load Sources**: Gets all active sources from database
2. **Initialize Schedules**: Creates sync schedule entries if they don't exist
3. **Check Due Tasks**: For each source and task type, checks if `next_sync <= now()`
4. **Acquire Locks**: Attempts to acquire per-task lock (prevents concurrent runs)
5. **Execute Sync**: Runs the specific sync method and logs results
6. **Update Schedule**: Updates `next_sync` timestamp for next execution
7. **Sleep**: Waits for configured interval, then repeats

### Per-Task-Type Task Types

The daemon manages 6 independent sync tasks:

| Task Type | Method | Description |
|-----------|--------|-------------|
| `live_categories` | `syncLiveCategories()` | Sync live TV categories |
| `live_streams` | `syncLiveStreams()` | Sync live TV streams |
| `vod_categories` | `syncVodCategories()` | Sync VOD categories |
| `vod_streams` | `syncVodStreams()` | Sync VOD streams |
| `series_categories` | `syncSeriesCategories()` | Sync series categories |
| `series` | `syncSeries()` | Sync series |

Each task type can have different sync intervals and schedules.

### Lock Mechanism

Locks prevent concurrent executions of the same task for the same source:

- Lock files: `/tmp/sync-{source_id}-{task_type}.lock`
- Lock timeout: 30 minutes (configurable via `SYNC_LOCK_TIMEOUT`)
- Automatic cleanup: Expired locks are removed automatically

### Heartbeat File

The daemon creates/updates `/tmp/sync-daemon-heartbeat` with the current timestamp on every iteration:

- Used for external health monitoring
- Can be checked via: `stat /tmp/sync-daemon-heartbeat`

### Logging

Logs are stored in two locations:

**File Logs** (`logs/iptv/sync-daemon.log`):
- Daily rotation (keeps 7 days of history)
- Contains INFO, WARNING, and ERROR level messages
- Includes execution timing and statistics

**Console Output** (stdout):
- INFO level and above
- Useful for monitoring in Docker containers

## Signal Handling

The daemon responds to these signals:

| Signal | Behavior |
|--------|----------|
| `SIGTERM` | Graceful shutdown - finishes current sync, then exits |
| `SIGINT` | Same as SIGTERM (Ctrl+C) |

## Memory Management

The daemon monitors memory usage:

- Checks memory on each iteration
- Logs current usage (MB)
- Restarts if usage exceeds configured limit (default: 256 MB)
- Useful for long-running processes in memory-constrained environments

## Docker Integration

### Recommended Docker Compose Entry

```yaml
sync-daemon:
  build: .
  command: php bin/sync-daemon.php
  environment:
    - SYNC_CHECK_INTERVAL=300
    - SYNC_LOCK_TIMEOUT=1800
    - SYNC_MEMORY_LIMIT=256
  volumes:
    - ./logs/iptv:/app/logs/iptv
  depends_on:
    - mysql
  healthcheck:
    test: ["CMD", "test", "-f", "/tmp/sync-daemon-heartbeat"]
    interval: 60s
    timeout: 10s
    retries: 3
    start_period: 10s
```

### Health Check

Docker health check monitors the heartbeat file:

```dockerfile
HEALTHCHECK --interval=60s --timeout=10s --retries=3 --start-period=10s \
  CMD test -f /tmp/sync-daemon-heartbeat
```

## Troubleshooting

### Daemon Not Running

Check logs:
```bash
tail -f logs/iptv/sync-daemon.log
```

Verify process:
```bash
ps aux | grep sync-daemon
```

### Syncs Not Executing

1. Check if source is active: `is_active = 1` in sources table
2. Verify `next_sync` is in the past for the task
3. Check for lock files: `ls -la /tmp/sync-*-*.lock`
4. Review logs for errors

### Memory Issues

Increase limit or reduce check interval:

```env
SYNC_MEMORY_LIMIT=512
SYNC_CHECK_INTERVAL=600
```

### Lock Files Stuck

Remove expired lock files:

```bash
find /tmp -name "sync-*.lock" -mmin +30 -delete
```

## Performance Tuning

### For High-Volume Syncs

```env
# Longer check interval to reduce CPU
SYNC_CHECK_INTERVAL=600

# Increase memory limit for larger datasets
SYNC_MEMORY_LIMIT=512
```

### For Frequent Updates

```env
# Shorter check interval for more responsive syncs
SYNC_CHECK_INTERVAL=120

# Shorter lock timeout for faster recovery
SYNC_LOCK_TIMEOUT=900
```

## Related Commands

### Manual Sync (On-Demand)

```bash
# Sync specific source and task
php bin/sync.php --source-id=1 --task-type=live_streams

# Sync all sources
php bin/sync-all-sources.php
```

### Database Migrations

```bash
# Run pending migrations
php bin/migrate.php
```

## Development

### Testing

Run the test suite:

```bash
php vendor/bin/phpunit tests/Unit/SyncDaemonTest.php
```

### Dry-Run Mode

Test the daemon without making database changes:

```bash
php bin/sync-daemon.php --dry-run --verbose
```

## Architecture

### Models

- **Source**: Upstream IPTV sources to sync from
- **SyncSchedule**: Per-source, per-task-type sync scheduling
- **SyncLog**: Historical record of all sync operations

### Services

- **SyncService**: Performs actual synchronization tasks
- **XtreamClient**: Connects to upstream Xtream servers

## License

See main project LICENSE file.
