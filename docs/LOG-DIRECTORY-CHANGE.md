# Log Directory Change Summary

## Change

Log directory changed from `/logs/sync-daemon` to `/logs/iptv`

## Reason

- Cleaner, shorter path
- Matches application name (IPTV)
- All logs consolidated in single directory `/logs/iptv/`
- Easier to type and remember
- Better organization and log management

## What Changed

### Configuration Files

| File | Change |
|------|--------|
| `back/bin/sync-daemon.sh` | `LOG_DIR` default: `/logs/sync-daemon` → `/logs/iptv` |
| `docker/docker-entrypoint.sh` | Daemon log path: `/logs/sync-daemon.log` → `/logs/iptv/sync-daemon.log` |
| `docker/Dockerfile` | Directory creation: `/logs/sync-daemon` → `/logs/iptv` |

### Documentation Files

All references updated in:
- ✅ `CHANGELOG-SYNC-DAEMON.md`
- ✅ `GRANULAR-SYNC.md`
- ✅ `IMPLEMENTATION-SUMMARY.md`
- ✅ `SYNC-DAEMON.md`
- ✅ `TIMEOUT-CONFIGURATION.md`
- ✅ `TIMEOUT-UPDATE-SUMMARY.md`
- ✅ `docs/SYNC_DAEMON.md`

## New Log Structure

```
/logs/
└── iptv/                                    # All application logs
    ├── sync-daemon.log                      # All sync operations (consolidated)
    ├── php-errors.log                       # PHP error log
    └── nginx-error.log                      # Nginx error log
```

All sync operations for all sources and task types are logged to a single unified file: `/logs/iptv/sync-daemon.log`

Each log entry includes the source name and task type for easy filtering:
```
[2025-01-15 10:30:00] INFO: Starting sync: MySource/live_streams
[2025-01-15 10:30:00] [MySource/live_streams] Syncing live streams...
[2025-01-15 10:30:05] INFO: Completed sync: MySource/live_streams (5s)
```

## Usage Examples

### View Main Daemon Log

```bash
# Old
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon.log

# New
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-daemon.log
```

### View Specific Task Type Logs

```bash
# Filter by source and task type
docker exec iptv-organizer-proxy grep "MySource/live_streams" /logs/iptv/sync-daemon.log

# Follow live updates for a specific task
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-daemon.log | grep "MySource/live_streams"
```

### View All Logs for a Source

```bash
# Filter by source name
docker exec iptv-organizer-proxy grep "MySource" /logs/iptv/sync-daemon.log

# Follow live updates for all tasks of a source
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-daemon.log | grep "MySource"
```

### Search for Errors

```bash
# Search all errors in sync log
docker exec iptv-organizer-proxy grep -i error /logs/iptv/sync-daemon.log

# Search errors for specific source
docker exec iptv-organizer-proxy grep -i error /logs/iptv/sync-daemon.log | grep "MySource"

# Search all errors in all logs
docker exec iptv-organizer-proxy grep -i error /logs/iptv/*.log
```

## Environment Variable

The log directory can still be customized via environment variable:

```yaml
# docker-compose.yml
environment:
  - LOG_DIR=/custom/log/path

# Or
docker run -e LOG_DIR=/custom/log/path iptv-organizer-proxy
```

Default: `/logs/iptv`

## Migration

**No migration needed!**

- Old containers: Will use `/logs/sync-daemon` (existing logs preserved)
- New containers: Will use `/logs/iptv` (fresh logs)
- If you rebuild: New location applies automatically
- If you want to preserve logs: Copy manually before rebuild

### Manual Log Migration (Optional)

```bash
# If you want to migrate existing logs
docker exec iptv-organizer-proxy sh -c "
    mkdir -p /logs/iptv
    cp -r /logs/sync-daemon/* /logs/iptv/ 2>/dev/null || true
"
```

## Verification

After rebuild/restart:

```bash
# Check daemon is using new location
docker exec iptv-organizer-proxy ps aux | grep sync-daemon

# Verify log directory exists
docker exec iptv-organizer-proxy ls -la /logs/iptv/

# Check daemon log file
docker exec iptv-organizer-proxy tail /logs/iptv/sync-daemon.log
```

## Rollback

If you need to revert to old path:

```bash
# Set environment variable
docker run -e LOG_DIR=/logs/sync-daemon iptv-organizer-proxy

# Or in docker-compose.yml
environment:
  - LOG_DIR=/logs/sync-daemon
```

## Summary

| Aspect | Old | New |
|--------|-----|-----|
| Main log path | `/logs/sync-daemon.log` | `/logs/iptv/sync-daemon.log` |
| Task log path | `/logs/sync-daemon/sync-{id}-{type}.log` | **Consolidated into main log** |
| Directory | `/logs/sync-daemon/` | `/logs/iptv/` |
| Log structure | Separate file per task | **Single unified log file** |
| Env variable | `LOG_DIR` (default: `/logs/sync-daemon`) | `LOG_DIR` (default: `/logs/iptv`) |
| Files changed | 10 files | - |
| Migration needed | ❌ No | - |

**Result**: Cleaner, more intuitive log directory structure that matches the application name.
