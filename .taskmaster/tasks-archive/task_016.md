# Task ID: 16

**Title:** Background Sync Worker - PHP Daemon with Task Separation

**Status:** pending

**Dependencies:** 6

**Priority:** high

**Description:** Implement long-running PHP daemon worker for automatic scheduled synchronization with support for all 6 sync task types per source

**Details:**

1. Create `bin/sync-daemon.php`:
   - Long-running PHP process (infinite loop)
   - Reads SYNC_CHECK_INTERVAL from env (default: 300 seconds = 5 minutes)
   - Main loop:
     - Load all active sources from database
     - For each source:
       - Check next_sync timestamp per task type
       - For each of 6 task types (live_categories, live_streams, vod_categories, vod_streams, series_categories, series):
         - If next_sync <= now() for that task type:
           - Create SyncService instance
           - Run specific sync task (e.g., syncLiveStreams())
           - Update next_sync = now() + sync_interval for that task type
           - Log to sync_logs with sync_type
       - Handle errors per task (don't stop entire daemon)
     - Sleep for SYNC_CHECK_INTERVAL seconds
     - Repeat loop
2. Graceful shutdown:
   - Register signal handlers (SIGTERM, SIGINT)
   - On signal, finish current sync and exit cleanly
   - Log shutdown event
3. Error handling:
   - Try/catch around each task
   - Log errors to file and database
   - Continue to next task on error
   - Send notification on repeated failures (optional)
4. Memory management:
   - Unset large variables after use
   - Monitor memory usage
   - Restart daemon if memory exceeds threshold (via Docker restart policy)
5. Logging:
   - Use Monolog with daily rotation
   - Log levels: INFO, WARNING, ERROR
   - Log to stdout for Docker logs
   - Log to file for persistence
6. Lock mechanism per task type:
   - Create lock file: /tmp/sync-{source_id}-{task_type}.lock
   - Check lock before starting task sync
   - Release lock after completion or timeout (30 minutes)
7. Health check:
   - Create /tmp/sync-daemon-heartbeat file every loop iteration
   - Update timestamp
   - Docker health check can monitor this file
8. Task scheduling:
   - Store next_sync timestamp per source AND per task type
   - Add columns to sources table or create separate sync_schedule table:
     - source_id, task_type, next_sync, last_sync
   - Each task can have different intervals (future enhancement)
9. Make script executable:
   - Add shebang: #!/usr/bin/env php
   - chmod +x bin/sync-daemon.php

**Test Strategy:**

1. Test daemon starts and enters main loop
2. Test daemon loads all active sources
3. Test daemon checks next_sync for each task type
4. Test daemon runs sync tasks at correct intervals
5. Test daemon handles errors without crashing
6. Test daemon logs to stdout and file
7. Test lock mechanism prevents duplicate syncs
8. Test graceful shutdown on SIGTERM
9. Test memory management keeps usage stable
10. Test health check file updates regularly
11. Run daemon for 24 hours and verify stability
12. Test all 6 sync task types execute correctly
