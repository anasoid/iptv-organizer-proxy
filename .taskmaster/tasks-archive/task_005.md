# Task ID: 5

**Title:** Label Extraction Engine & Sync Service Core

**Status:** pending

**Dependencies:** 4

**Priority:** high

**Description:** Implement label extraction from channel/category names and create core synchronization service with task separation for live categories and streams

**Details:**

1. Create `src/Services/LabelExtractor.php`:
   - Static method extractLabels($text, $streamType)
   - Split by '-' delimiter, trim whitespace, add to labels array
   - Split by '|' delimiter, trim whitespace, add to labels array
   - Extract text between '[' and ']' as separate labels
   - Remove '[]' brackets from main text for clean labels
   - Add $streamType to labels (live, movie, series)
   - Return comma-separated string: "ESPN,HD,Sports,USA,live"
   - Examples:
     - "ESPN [HD] | Sports - USA" + "live" → "ESPN,HD,Sports,USA,live"
     - "FR: TF1 [FHD]" + "live" → "FR,TF1,FHD,live"
     - "Movies - Action | English" + "movie" → "Movies,Action,English,movie"
2. Create `src/Services/SyncService.php`:
   - Constructor accepts Source model, XtreamClient, Logger
   - Method: syncLiveCategories()
     - Fetch categories from XtreamClient
     - Extract labels from category_name
     - Insert/update categories table
     - Log to sync_logs with sync_type='live_categories'
     - Return stats (added, updated, deleted)
   - Method: syncLiveStreams()
     - Fetch streams from XtreamClient
     - For each stream:
       - Extract labels from name using LabelExtractor
       - Store essential fields: source_id, stream_id, name, category_id, category_ids, is_adult, labels, is_active
       - Store complete API response in data JSON field
       - Handle category_ids as JSON array
     - Insert/update live_streams table
     - Mark missing streams as inactive (soft delete)
     - Log to sync_logs with sync_type='live_streams'
     - Return stats
   - Method: syncVodCategories() - similar to syncLiveCategories()
   - Method: syncVodStreams() - similar to syncLiveStreams()
   - Method: syncSeriesCategories() - similar to syncLiveCategories()
   - Method: syncSeries() - similar to syncLiveStreams()
3. Implement sync lock mechanism:
   - Use database table or file lock per task type
   - Prevent duplicate syncs: check if sync_type for source is already running
   - Automatically release lock after completion or timeout
4. Incremental sync logic:
   - Compare last_modified or added timestamps
   - Only update changed streams
   - Mark deleted streams as is_active=0
5. Transaction handling:
   - Wrap sync operations in database transactions
   - Rollback on error
6. Progress tracking:
   - Emit events or update database with current progress
   - Allow UI to poll for sync status

**Test Strategy:**

1. Unit test LabelExtractor with various input formats
2. Test label extraction handles special characters
3. Test sync methods insert new records correctly
4. Test sync methods update existing records
5. Test sync marks deleted streams as inactive
6. Test sync lock prevents duplicate syncs
7. Test transaction rollback on errors
8. Test sync_logs table records all sync operations
9. Test incremental sync only updates changed records
10. Integration test with mock XtreamClient data
