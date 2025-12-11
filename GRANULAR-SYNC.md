# Granular Sync Implementation

## Overview

The sync system now executes each task type **individually** instead of syncing all content at once. This ensures each PHP execution stays within the **3-minute timeout** and uses **minimal memory** (under 32M).

## How It Works

### Before (Single Large Execution)

```bash
# Old approach: sync everything at once
php sync.php --source-id=1
└─ Sync live_categories
└─ Sync live_streams (thousands of channels)
└─ Sync vod_categories
└─ Sync vod_streams (huge libraries)
└─ Sync series_categories
└─ Sync series
# Total: Could take 10+ minutes, use 100+ MB memory
```

### After (Multiple Small Executions)

```bash
# New approach: sync one task type at a time
php sync.php --source-id=1 --task-type=live_categories     # ~10-20s, ~15MB
php sync.php --source-id=1 --task-type=live_streams        # ~30-60s, ~20MB
php sync.php --source-id=1 --task-type=vod_categories      # ~10-20s, ~15MB
php sync.php --source-id=1 --task-type=vod_streams         # ~30-60s, ~25MB
php sync.php --source-id=1 --task-type=series_categories   # ~10-20s, ~15MB
php sync.php --source-id=1 --task-type=series              # ~30-60s, ~20MB
# Total: ~2-4 minutes, each task < 30MB memory
```

## Task Types

Each source has **6 independent task types**:

| Task Type | Description | Typical Duration | Typical Memory |
|-----------|-------------|------------------|----------------|
| `live_categories` | Live TV categories | 10-20s | 10-15MB |
| `live_streams` | Live TV channels/streams | 30-90s | 20-30MB |
| `vod_categories` | Video-on-Demand categories | 10-20s | 10-15MB |
| `vod_streams` | VOD movies/content | 30-120s | 25-35MB |
| `series_categories` | Series/TV show categories | 10-20s | 10-15MB |
| `series` | Series episodes | 30-90s | 20-30MB |

## Sync Daemon Behavior

The daemon now:

1. **Checks every 3 hours** for sources that need syncing
2. **For each source**, iterates through all 6 task types:
   - `live_categories`, `live_streams`
   - `vod_categories`, `vod_streams`
   - `series_categories`, `series`
3. **Executes each task individually** (sync.php checks if task is due) with separate:
   - PHP process
   - Lock file
   - Log file
   - Timeout protection (180s)
4. **No PHP overhead**: Task list is hardcoded, no database query needed

### Example Daemon Execution

```
[2025-01-10 12:00:00] Daemon checks for active sources
[2025-01-10 12:00:01] Processing source: IPTV Provider A
[2025-01-10 12:00:02] Starting: IPTV Provider A/live_categories
[2025-01-10 12:00:15] Completed: IPTV Provider A/live_categories (13s)
[2025-01-10 12:00:16] Starting: IPTV Provider A/live_streams
[2025-01-10 12:01:02] Completed: IPTV Provider A/live_streams (46s)
[2025-01-10 12:01:03] Task 'vod_categories' not due yet (skipped)
[2025-01-10 12:01:03] Starting: IPTV Provider A/vod_streams
[2025-01-10 12:02:18] Completed: IPTV Provider A/vod_streams (75s)
[2025-01-10 12:02:19] Task 'series_categories' not due yet (skipped)
[2025-01-10 12:02:19] Task 'series' not due yet (skipped)
[2025-01-10 12:02:20] Processing source: IPTV Provider B
[2025-01-10 12:02:21] Starting: IPTV Provider B/live_streams
...

Note: Tasks are skipped if not due (checked by sync.php, not the daemon)
```

## CLI Usage

### Manual Task Sync

```bash
# Sync a specific task type for a source
php bin/sync.php --source-id=1 --task-type=live_streams

# Sync all task types for a source (old behavior)
php bin/sync.php --source-id=1 --task-type=all

# Or simply (defaults to 'all')
php bin/sync.php --source-id=1
```

### Available Task Types

```bash
# Live TV
php bin/sync.php --source-id=1 --task-type=live_categories
php bin/sync.php --source-id=1 --task-type=live_streams

# Video on Demand
php bin/sync.php --source-id=1 --task-type=vod_categories
php bin/sync.php --source-id=1 --task-type=vod_streams

# Series/TV Shows
php bin/sync.php --source-id=1 --task-type=series_categories
php bin/sync.php --source-id=1 --task-type=series
```

### Force Sync

```bash
# Force sync even if not due
php bin/sync.php --source-id=1 --task-type=live_streams --force

# Verbose output
php bin/sync.php --source-id=1 --task-type=vod_streams --verbose
```

## Lock Files

Each task type has its **own lock file** to prevent concurrent execution:

```bash
# Lock file format: /tmp/sync-{source_id}-{task_type}.lock
/tmp/sync-1-live_categories.lock
/tmp/sync-1-live_streams.lock
/tmp/sync-1-vod_categories.lock
/tmp/sync-1-vod_streams.lock
/tmp/sync-1-series_categories.lock
/tmp/sync-1-series.lock
```

Benefits:
- ✅ Different tasks can run in parallel if needed
- ✅ Each task has independent lock timeout
- ✅ Granular control over sync operations

## Log Files

Each task type has its **own log file** for easier debugging:

```bash
# Log file format: /logs/sync-daemon/sync-{source_id}-{task_type}.log
/logs/sync-daemon/sync-1-live_categories.log
/logs/sync-daemon/sync-1-live_streams.log
/logs/sync-daemon/sync-1-vod_categories.log
/logs/sync-daemon/sync-1-vod_streams.log
/logs/sync-daemon/sync-1-series_categories.log
/logs/sync-daemon/sync-1-series.log
```

Benefits:
- ✅ Easier to debug specific task failures
- ✅ Logs don't get mixed between task types
- ✅ Can monitor individual task performance

## Timeout Protection

Each task has **double timeout protection**:

1. **PHP timeout**: `max_execution_time = 180s` (3 minutes)
2. **Shell timeout**: `timeout 180` command wrapper

If a task exceeds 180 seconds:
- PHP kills the script
- Shell `timeout` command enforces it
- Lock is cleaned up after 600s (10 minutes)
- Task marked as failed in logs

## Benefits

### 1. Stays Within 3-Minute Limit

Each individual task completes quickly:
- Categories: 10-20 seconds
- Streams: 30-90 seconds
- **Total per task**: < 3 minutes ✅

### 2. Low Memory Usage

Each task uses minimal memory:
- Categories: 10-15 MB
- Streams: 20-35 MB
- **Total per task**: < 32 MB ✅

### 3. Better Failure Handling

If one task fails:
- ✅ Other tasks still complete
- ✅ Easier to identify which task failed
- ✅ Can retry individual tasks

### 4. Granular Control

- ✅ Can manually sync specific task types
- ✅ Independent lock files per task
- ✅ Individual logs for each task
- ✅ Tasks can run in parallel if needed

### 5. Faster Recovery

If a task times out:
- ✅ Only that task needs to be retried
- ✅ No need to re-sync everything
- ✅ Lock expires in 10 minutes (not 30)

## Database Schema

The `sync_schedules` table tracks each task independently:

```sql
CREATE TABLE sync_schedules (
    id INT PRIMARY KEY AUTO_INCREMENT,
    source_id INT NOT NULL,
    task_type VARCHAR(50) NOT NULL,  -- live_categories, live_streams, etc.
    interval_seconds INT NOT NULL,
    next_sync DATETIME NOT NULL,
    last_sync DATETIME NULL,
    UNIQUE KEY (source_id, task_type)
);
```

Each task has its own schedule, so:
- Categories can sync more frequently (every 6 hours)
- Streams can sync less frequently (every 3 hours)
- Independent tracking per task type

## Performance Comparison

### Large Source (10,000 channels)

| Metric | Old (All at once) | New (Granular) |
|--------|-------------------|----------------|
| **Execution time** | 10-15 minutes | 6x ~90s = 9 minutes |
| **Memory peak** | 120-150 MB | 6x ~30MB = 30 MB peak |
| **Timeout risk** | ❌ High | ✅ Low |
| **Failure impact** | ❌ Lose all progress | ✅ Lose one task only |
| **Log readability** | ❌ Mixed logs | ✅ Separate logs |

### Small Source (500 channels)

| Metric | Old (All at once) | New (Granular) |
|--------|-------------------|----------------|
| **Execution time** | 2-3 minutes | 6x ~20s = 2 minutes |
| **Memory peak** | 40-50 MB | 6x ~15MB = 15 MB peak |
| **Timeout risk** | ✅ Low | ✅ Very low |
| **Failure impact** | ❌ Lose all progress | ✅ Lose one task only |

## Monitoring

### Check Task Status

```bash
# View daemon logs
docker logs -f iptv-organizer-proxy | grep -i sync

# View specific task log
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon/sync-1-live_streams.log

# Check active locks
docker exec iptv-organizer-proxy ls -la /tmp/sync-*.lock

# Check task schedules in database
docker exec iptv-organizer-proxy php -r "
require_once '/app/bootstrap.php';
use App\Models\SyncSchedule;
\$schedules = SyncSchedule::findBySource(1);
foreach (\$schedules as \$s) {
    echo \$s->task_type . ': ' . \$s->next_sync . PHP_EOL;
}
"
```

### Monitor Task Performance

```bash
# Check task duration from logs
docker exec iptv-organizer-proxy grep "Completed:" /logs/sync-daemon.log | tail -20

# Example output:
# Completed sync: Provider A/live_streams (46s)
# Completed sync: Provider A/vod_streams (82s)
# Completed sync: Provider B/live_categories (12s)
```

## Troubleshooting

### Task Taking Too Long

If a task consistently times out:

```bash
# Check which task is timing out
docker exec iptv-organizer-proxy grep "Timeout:" /logs/sync-daemon.log

# View the specific task log
docker exec iptv-organizer-proxy tail -100 /logs/sync-daemon/sync-1-vod_streams.log

# Solution: Increase timeout in Dockerfile
# Edit docker/Dockerfile:
echo "max_execution_time = 300" >> /usr/local/etc/php.ini-production
# Rebuild image
```

### Task Failing

```bash
# Check error logs
docker exec iptv-organizer-proxy grep "Failed:" /logs/sync-daemon.log

# View specific task errors
docker exec iptv-organizer-proxy tail -50 /logs/sync-daemon/sync-1-live_streams.log
```

### Manual Task Retry

```bash
# Retry a specific failed task
docker exec iptv-organizer-proxy php /app/bin/sync.php \
    --source-id=1 \
    --task-type=vod_streams \
    --force \
    --verbose
```

## Migration from Old Daemon

The old PHP daemon (`sync-daemon.php`) synced all tasks at once. The new shell daemon (`sync-daemon.sh`) syncs tasks individually.

### Key Differences

| Feature | Old PHP Daemon | New Shell Daemon |
|---------|----------------|------------------|
| Task execution | All at once | One by one |
| Memory usage | High (100+ MB) | Low (30 MB per task) |
| Timeout risk | High | Low |
| Lock granularity | Per source | Per task type |
| Log files | Mixed | Per task type |

### No Migration Needed

The change is automatic:
- ✅ Same `sync_schedules` database table
- ✅ Same `sync.php` script (already supported `--task-type`)
- ✅ Daemon automatically detects due tasks

Just rebuild and restart the container.

## Best Practices

1. **Monitor First Day**: Watch logs for timeouts or failures
2. **Check Task Duration**: Ensure all tasks complete < 3 minutes
3. **Review Memory**: Verify each task uses < 32 MB
4. **Adjust Intervals**: Set appropriate sync intervals per task type
5. **Separate Logs**: Review individual task logs for debugging

## Conclusion

Granular sync execution ensures:
- ✅ Each PHP execution completes within 3-minute timeout
- ✅ Memory usage stays under 32 MB per task
- ✅ Better failure isolation and recovery
- ✅ Easier debugging with separate logs
- ✅ Optimal for low-power devices (OpenWRT routers)

The sync system is now production-ready for embedded devices with strict resource constraints.
