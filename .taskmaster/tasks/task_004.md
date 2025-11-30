# Task ID: 4

**Title:** Xtream Codes API Client Library

**Status:** pending

**Dependencies:** 3

**Priority:** high

**Description:** Implement HTTP client to fetch data from upstream Xtream Codes servers, handle authentication, and parse all API endpoints

**Details:**

1. Create `src/Services/XtreamClient.php` class:
   - Constructor accepts source credentials (url, username, password)
   - Uses Guzzle HTTP client
   - Base URL: {source_url}/player_api.php
2. Implement authentication method:
   - authenticate() - calls player_api.php?username=X&password=Y
   - Returns server_info and user_info JSON
   - Validates credentials work
3. Implement category fetch methods:
   - getLiveCategories() - action=get_live_categories
   - getVodCategories() - action=get_vod_categories
   - getSeriesCategories() - action=get_series_categories
   - Returns array of categories
4. Implement stream fetch methods:
   - getLiveStreams($categoryId = null) - action=get_live_streams
   - getVodStreams($categoryId = null) - action=get_vod_streams
   - getSeries($categoryId = null) - action=get_series
   - getSeriesInfo($seriesId) - action=get_series_info&series_id=X
   - Returns array of streams/series
5. Implement EPG methods:
   - getSimpleDataTable($streamId) - action=get_simple_data_table&stream_id=X
   - getShortEpg($streamId, $limit) - action=get_short_epg&stream_id=X&limit=Y
   - getXmltv($streamId = null) - GET /xmltv.php?username=X&password=Y[&stream_id=Z]
   - Returns raw EPG data (not parsed)
6. Error handling:
   - Catch Guzzle exceptions
   - Retry logic for network failures (3 retries with exponential backoff)
   - Log errors with Monolog
   - Throw custom XtreamApiException on failure
7. Response parsing:
   - Validate JSON responses
   - Handle empty responses gracefully
   - Parse pagination if needed
8. Rate limiting:
   - Add optional delay between requests (configurable)
   - Prevent hammering source servers

**Test Strategy:**

1. Mock Guzzle client for unit tests
2. Test authentication with valid/invalid credentials
3. Test each API endpoint returns expected data structure
4. Test error handling for network failures
5. Test retry logic executes correctly
6. Test JSON parsing handles malformed responses
7. Integration test with real Xtream server (manual)
8. Test rate limiting prevents excessive requests
9. Verify all API methods return correct data types
