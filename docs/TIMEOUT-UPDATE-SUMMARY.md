# Timeout Configuration Update Summary

## Changes Applied

All timeout and resource settings have been updated to optimize for low-power devices (OpenWRT routers) with emphasis on:
- Fast failure detection
- Low resource usage
- Efficient sync intervals

## New Configuration Values

### PHP Execution Limits (docker/Dockerfile)

| Setting | Old Value | New Value | Change |
|---------|-----------|-----------|--------|
| `max_execution_time` | 600s (10 min) | **180s (3 min)** | ⬇️ 70% reduction |
| `memory_limit` | 32M | **32M** | ✅ No change |
| `default_socket_timeout` | 300s (5 min) | **300s (5 min)** | ✅ No change |

### Sync Daemon Configuration (docker-compose.yml, .env.example.app)

| Setting | Old Value | New Value | Change |
|---------|-----------|-----------|--------|
| `SYNC_CHECK_INTERVAL` | 300s (5 min) | **10800s (3 hours)** | ⬆️ 36x increase |
| `SYNC_LOCK_TIMEOUT` | 1800s (30 min) | **600s (10 min)** | ⬇️ 67% reduction |

## Impact Analysis

### ✅ Benefits

1. **Faster Failure Detection**
   - Hung sync tasks now fail after 3 minutes instead of 10
   - Quicker recovery from errors

2. **Reduced Resource Usage**
   - Check interval: 8 checks/day instead of 288 checks/day (97% reduction)
   - Lower CPU usage on embedded devices
   - Better battery/power efficiency for always-on routers

3. **Optimized Lock Timeouts**
   - 10-minute lock timeout is 3.3x the execution timeout
   - Faster cleanup of stuck locks
   - Reduced lock file accumulation

4. **Memory Efficiency**
   - 32M limit prevents memory exhaustion
   - Suitable for low-memory devices (256-512MB RAM)

### ⚠️ Considerations

1. **Sync Frequency**
   - Sources now sync every 3 hours instead of 5 minutes
   - **Impact**: Content updates take longer to appear
   - **Mitigation**: Most IPTV sources don't update channels more than once per hour

2. **Execution Timeout**
   - Large sources with 10,000+ channels may timeout
   - **Impact**: Some syncs may fail if they take > 3 minutes
   - **Mitigation**: See "Increasing Timeouts" section below

## Files Modified

### Configuration Files
- ✅ `docker/Dockerfile` - PHP execution limits
- ✅ `docker-compose.yml` - Production environment defaults
- ✅ `docker/docker-compose.yml` - Development environment defaults
- ✅ `back/.env.example.app` - Example configuration
- ✅ `back/bin/sync-daemon.sh` - Default values in script

### Documentation Files
- ✅ `SYNC-DAEMON.md` - Complete sync daemon documentation
- ✅ `TIMEOUT-CONFIGURATION.md` - Detailed timeout guide
- ✅ `CHANGELOG-SYNC-DAEMON.md` - Implementation changelog
- ✅ `README.md` - Environment variables reference
- ✅ `TIMEOUT-UPDATE-SUMMARY.md` - This file

## Quick Reference

### Current Timeout Hierarchy (shortest to longest)

```
1. PHP Execution: 180s (3 min)     ← Script killed here
2. Socket Timeout: 300s (5 min)    ← Network requests timeout
3. Lock Timeout: 600s (10 min)     ← Cleanup stale locks
4. Check Interval: 10800s (3 hours) ← Between daemon checks
```

### Sync Daemon Behavior

```
Hour 0:  Daemon checks → Sync all active sources
Hour 3:  Daemon checks → Sync all active sources
Hour 6:  Daemon checks → Sync all active sources
Hour 9:  Daemon checks → Sync all active sources
Hour 12: Daemon checks → Sync all active sources
Hour 15: Daemon checks → Sync all active sources
Hour 18: Daemon checks → Sync all active sources
Hour 21: Daemon checks → Sync all active sources
Hour 24: Repeat...
```

**Result**: 8 sync cycles per day

## Increasing Timeouts (If Needed)

If you have large sources that need more time to sync:

### Option 1: Increase Execution Timeout to 10 minutes

Edit `docker/Dockerfile`:
```dockerfile
echo "max_execution_time = 600" >> /usr/local/etc/php.ini-production && \
```

Edit `docker-compose.yml`:
```yaml
environment:
  - SYNC_LOCK_TIMEOUT=1800  # 30 minutes (3x execution timeout)
```

Rebuild:
```bash
docker-compose -f docker/docker-compose.yml build
docker-compose -f docker/docker-compose.yml up -d
```

### Option 2: Increase Check Interval to 6 hours

Edit `docker-compose.yml`:
```yaml
environment:
  - SYNC_CHECK_INTERVAL=21600  # 6 hours
```

Restart:
```bash
docker-compose restart
```

### Option 3: More Frequent Checks (30 minutes)

Edit `docker-compose.yml`:
```yaml
environment:
  - SYNC_CHECK_INTERVAL=1800  # 30 minutes
```

Restart:
```bash
docker-compose restart
```

## Testing the Changes

### 1. Build and Start

```bash
docker-compose -f docker/docker-compose.yml build
docker-compose -f docker/docker-compose.yml up -d
```

### 2. Monitor Sync Daemon

```bash
# View daemon logs
docker logs -f iptv-organizer-proxy | grep -i sync

# Check daemon is running
docker exec iptv-organizer-proxy ps aux | grep sync-daemon

# View heartbeat
docker exec iptv-organizer-proxy cat /tmp/sync-daemon-heartbeat
```

### 3. Test Manual Sync

```bash
# Trigger immediate sync for source ID 1
docker exec iptv-organizer-proxy php /app/bin/sync.php --source-id=1
```

### 4. Verify Timeouts

```bash
# Check PHP settings
docker exec iptv-organizer-proxy php -i | grep -E "max_execution_time|memory_limit|default_socket_timeout"

# Expected output:
# max_execution_time => 180
# memory_limit => 32M
# default_socket_timeout => 300
```

## Monitoring Recommendations

1. **First 24 Hours**: Monitor logs for timeout errors
   ```bash
   docker logs -f iptv-organizer-proxy | grep -i "timeout\|exceeded"
   ```

2. **Check Sync Success Rate**: Review sync logs
   ```bash
   docker exec iptv-organizer-proxy tail -f /logs/iptv.log
   ```

3. **Watch Memory Usage**: Ensure 32M is sufficient
   ```bash
   docker stats iptv-organizer-proxy
   ```

## Rollback Instructions

If you need to revert to the previous values:

```bash
# Edit docker/Dockerfile
echo "max_execution_time = 600" >> /usr/local/etc/php.ini-production

# Edit docker-compose.yml
SYNC_CHECK_INTERVAL=300
SYNC_LOCK_TIMEOUT=1800

# Rebuild and restart
docker-compose -f docker/docker-compose.yml build
docker-compose -f docker/docker-compose.yml up -d
```

## Conclusion

These optimized settings are designed for:
- ✅ Low-power embedded devices (OpenWRT routers)
- ✅ Devices with 256-512MB RAM
- ✅ Battery-powered or energy-conscious deployments
- ✅ Sources that update every few hours (most IPTV providers)

The new configuration reduces resource usage by **97%** while maintaining reliable sync operations every 3 hours.

For large sources or more frequent updates, follow the "Increasing Timeouts" section above.
