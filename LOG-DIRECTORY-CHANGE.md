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
    ├── sync-daemon.log                      # Main daemon log
    ├── sync-1-live_categories.log           # Source 1, live categories
    ├── sync-1-live_streams.log              # Source 1, live streams
    ├── sync-1-vod_categories.log            # Source 1, VOD categories
    ├── sync-1-vod_streams.log               # Source 1, VOD streams
    ├── sync-1-series_categories.log         # Source 1, series categories
    ├── sync-1-series.log                    # Source 1, series
    ├── sync-2-live_categories.log           # Source 2, live categories
    ├── ...                                  # Additional sources
    ├── php-errors.log                       # PHP error log
    └── nginx-error.log                      # Nginx error log
```

## Usage Examples

### View Main Daemon Log

```bash
# Old
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon.log

# New
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-daemon.log
```

### View Specific Task Log

```bash
# Old
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon/sync-1-live_streams.log

# New
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-1-live_streams.log
```

### View All Logs for a Source

```bash
# Old
docker exec iptv-organizer-proxy tail -f /logs/sync-daemon/sync-1-*.log

# New
docker exec iptv-organizer-proxy tail -f /logs/iptv/sync-1-*.log
```

### Grep for Errors

```bash
# Old
docker exec iptv-organizer-proxy grep -i error /logs/sync-daemon/*.log

# New
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
| Task log path | `/logs/sync-daemon/sync-{id}-{type}.log` | `/logs/iptv/sync-{id}-{type}.log` |
| Directory | `/logs/sync-daemon/` | `/logs/iptv/` |
| Env variable | `LOG_DIR` (default: `/logs/sync-daemon`) | `LOG_DIR` (default: `/logs/iptv`) |
| Files changed | 10 files | - |
| Migration needed | ❌ No | - |

**Result**: Cleaner, more intuitive log directory structure that matches the application name.
