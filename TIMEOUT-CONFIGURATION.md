# PHP Task Timeout Configuration

## Summary

PHP sync tasks now have proper timeout protection to prevent hung processes and ensure reliable operation.

## Current Timeout Settings

### PHP Execution Timeouts (set in `docker/Dockerfile`)

| Setting | Value | Purpose |
|---------|-------|---------|
| `max_execution_time` | **180 seconds (3 minutes)** | Maximum time a single PHP sync task can run |
| `default_socket_timeout` | **300 seconds (5 minutes)** | Timeout for HTTP/network operations |
| `memory_limit` | **32M** | Maximum memory per PHP process |

### Sync Daemon Timeouts (configurable via environment)

| Setting | Default | Purpose |
|---------|---------|---------|
| `SYNC_CHECK_INTERVAL` | **10800s (3 hours)** | How often daemon checks for sources to sync |
| `SYNC_LOCK_TIMEOUT` | **600s (10 min)** | Lock expiration time |

## Timeout Flow

Here's how timeouts work in practice:

```
1. Sync daemon starts sync task for a source
   ↓
2. PHP script begins execution (32M memory limit)
   ↓
3. HTTP request to upstream source
   └→ Timeout after 300s (5 min) if source doesn't respond
   ↓
4. Processing data, updating database
   └→ Total execution timeout after 180s (3 min)
   ↓
5. If timeout exceeded:
   - PHP script terminates
   - Lock released after 600s (10 min) if cleanup fails
   - Sync marked as failed in logs
```

## Why These Values?

### `max_execution_time = 180s` (3 minutes)
- **Fast sync focus**: Forces efficient sync operations
- **Small sources**: Sync in 30-60 seconds
- **Medium sources**: Sync in 1-2 minutes
- **Quick failure detection**: Hung tasks fail fast
- **Resource efficiency**: Prevents long-running processes

### `default_socket_timeout = 300s` (5 minutes)
- **Normal requests**: Complete in seconds
- **Slow servers**: Up to 5 minutes allowed
- **Dead servers**: Fail after 5 minutes
- **Note**: Longer than execution timeout to allow for slow initial connections

### `SYNC_LOCK_TIMEOUT = 600s` (10 minutes)
- **3.3x execution timeout**: Ensures locks don't expire during valid sync
- **Cleanup protection**: Handles crashed processes
- **Quick recovery**: Failed syncs unlock within 10 minutes

### `SYNC_CHECK_INTERVAL = 10800s` (3 hours)
- **Low resource usage**: Checks only 8 times per day
- **Battery friendly**: Ideal for low-power devices (OpenWRT)
- **Most sources update**: Content changes every few hours
- **Reduced load**: Less frequent upstream API calls

## When to Adjust Timeouts

### Increase Timeouts If:

1. **Large channel lists**: Sources with 10,000+ channels
2. **Slow upstream servers**: Consistent timeouts in logs
3. **Complex processing**: Heavy filtering or transformations
4. **Network latency**: International sources with high latency

### Example: Increase to 10 minutes for large sources

Edit `docker/Dockerfile`:
```bash
echo "max_execution_time = 600" >> /usr/local/etc/php.ini-production && \
echo "default_socket_timeout = 300" >> /usr/local/etc/php.ini-production && \
```

Then rebuild:
```bash
docker-compose -f docker/docker-compose.yml build
```

And adjust lock timeout in `docker-compose.yml`:
```yaml
environment:
  - SYNC_LOCK_TIMEOUT=1800  # 30 minutes
```

### Decrease Timeouts If:

1. **Fast sources**: All sources sync in < 2 minutes
2. **Resource constraints**: Limited CPU/memory available
3. **Quick failure detection**: Want to detect issues faster

## Monitoring Timeouts

### Check for Timeout Issues

```bash
# View sync daemon logs
docker exec iptv-organizer-proxy tail -f /logs/iptv.log

# Check for timeout errors
docker exec iptv-organizer-proxy grep -i "timeout\|exceeded" /logs/iptv/*.log

# View PHP errors
docker exec iptv-organizer-proxy tail -f /logs/iptv/php-errors.log
```

### Symptoms of Insufficient Timeout

- Frequent "Maximum execution time exceeded" errors
- Syncs consistently failing for certain sources
- Lock files accumulating in `/tmp/sync-*.lock`

### Symptoms of Excessive Timeout

- Hung processes consuming resources
- Long waits for obviously dead sources
- Daemon iterations taking too long

## Timeout Hierarchy

From shortest to longest:

1. **Execution timeout** (180s) - Total PHP script runtime
2. **Socket timeout** (300s) - Individual HTTP requests
3. **Lock timeout** (600s) - Cleanup stale locks
4. **Check interval** (10800s) - Between daemon iterations

This hierarchy ensures:
- Fast failure for hung scripts (3 min)
- Adequate time for slow network requests (5 min)
- Quick cleanup of stuck processes (10 min)
- Efficient resource usage with long check intervals (3 hours)

## Troubleshooting

### Script Times Out on Large Sources

**Solution**: Increase `max_execution_time` from 180s to 600s
```dockerfile
echo "max_execution_time = 600" >> /usr/local/etc/php.ini-production
```

### Network Requests Timeout

**Solution**: Increase `default_socket_timeout`
```dockerfile
echo "default_socket_timeout = 600" >> /usr/local/etc/php.ini-production
```

### Locks Not Cleaning Up

**Solution**: Increase `SYNC_LOCK_TIMEOUT` in docker-compose from 600s to 1200s
```yaml
environment:
  - SYNC_LOCK_TIMEOUT=1200
```

### Sync Takes Too Long Overall

**Options**:
1. Increase timeouts (if legitimate)
2. Optimize sync logic (if inefficient)
3. Disable slow sources (if problematic)
4. Increase check interval (if too frequent)

## Best Practices

1. **Monitor first**: Check logs before adjusting timeouts
2. **Increase gradually**: Double timeout, test, repeat if needed
3. **Balance timeouts**: Keep lock timeout > execution timeout
4. **Document changes**: Note why timeouts were adjusted
5. **Test after changes**: Verify syncs complete successfully

## Files Containing Timeout Configuration

| File | Setting | Default |
|------|---------|---------|
| `docker/Dockerfile` | `max_execution_time` | 180 |
| `docker/Dockerfile` | `default_socket_timeout` | 300 |
| `docker/Dockerfile` | `memory_limit` | 32M |
| `docker-compose.yml` | `SYNC_LOCK_TIMEOUT` | 600 |
| `docker-compose.yml` | `SYNC_CHECK_INTERVAL` | 10800 |
| `.env.example.app` | `SYNC_LOCK_TIMEOUT` | 600 |
| `.env.example.app` | `SYNC_CHECK_INTERVAL` | 10800 |

## Summary Table

| Timeout Type | Value | Scope | Can Hang Forever Without It? |
|--------------|-------|-------|------------------------------|
| Execution timeout | 3 min | Entire sync task | ✅ Yes |
| Socket timeout | 5 min | Single HTTP request | ✅ Yes |
| Lock timeout | 10 min | Sync operation | ❌ No (daemon cleanup) |
| Check interval | 3 hours | Daemon iteration | ❌ No (just frequency) |
| Memory limit | 32M | PHP process | ✅ Yes (memory exhaustion) |

**Result**: With these timeouts, no sync task can hang forever. Maximum hang time is 3 minutes before PHP kills it.
