# Task ID: 6

**Title:** CLI Sync Script & Manual Sync Tool

**Status:** pending

**Dependencies:** 5

**Priority:** medium

**Description:** Create command-line interface for manually triggering synchronization tasks per source and task type

**Details:**

1. Create `bin/sync.php` CLI script:
   - Accepts command-line arguments:
     - --source-id=X (required)
     - --task-type=TYPE (optional, values: live_categories, live_streams, vod_categories, vod_streams, series_categories, series, all)
     - --force (ignore sync interval and lock)
   - If no --task-type, sync all tasks for the source
2. Script logic:
   - Load environment variables
   - Connect to database
   - Load Source model by ID
   - Validate source exists and is active
   - Create XtreamClient with source credentials
   - Create SyncService instance
   - If task-type specified, run that specific sync task
   - If task-type=all or not specified, run all 6 sync tasks in sequence:
     1. syncLiveCategories()
     2. syncLiveStreams()
     3. syncVodCategories()
     4. syncVodStreams()
     5. syncSeriesCategories()
     6. syncSeries()
   - Handle each task separately with error handling
   - Log output to console and file
   - Exit with appropriate status code (0 success, 1 error)
3. Output formatting:
   - Display progress for each task
   - Show stats (items added/updated/deleted)
   - Color-coded output (success green, errors red)
   - Verbose mode with --verbose flag
4. Error handling:
   - Catch exceptions per task
   - Continue to next task on error (don't stop entire sync)
   - Log detailed error messages
   - Send email/webhook notification on critical errors (configurable)
5. Make script executable:
   - Add shebang: #!/usr/bin/env php
   - chmod +x bin/sync.php
6. Create helper script `bin/sync-all-sources.php`:
   - Sync all active sources
   - Useful for cron jobs or scheduled tasks

**Test Strategy:**

1. Test script runs with valid source ID
2. Test script fails gracefully with invalid source ID
3. Test --task-type parameter works for each type
4. Test --force flag bypasses sync lock
5. Test script outputs correct stats
6. Test error handling for network failures
7. Test script exits with correct status codes
8. Test sync-all-sources.php syncs multiple sources
9. Manual test: run sync and verify database updated
10. Test verbose output shows detailed logs
