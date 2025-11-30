# Task ID: 11

**Title:** VOD & Series API Endpoints Implementation

**Status:** pending

**Dependencies:** 10

**Priority:** medium

**Description:** Implement get_vod_categories, get_vod_streams, get_series_categories, get_series, and get_series_info endpoints with filtering

**Details:**

1. Extend `src/Controllers/XtreamController.php`:
   - Method: getVodCategories($request, $response)
     - Query categories WHERE source_id=client.source_id AND category_type='vod'
     - Apply filter if assigned
     - Generate favoris virtual categories for VOD (100000+)
     - Return favoris FIRST, then regular categories
   - Method: getVodStreams($request, $response)
     - Query vod_streams WHERE source_id=client.source_id
     - Filter by category_id if provided (including favoris)
     - Apply filter rules (include/exclude)
     - Apply adult content filter if hide_adult_content=1
     - Extract data from JSON field
     - Construct VOD URL: http://proxy-url/movie/{username}/{password}/{stream_id}.{container_extension}
     - Return VOD streams array
   - Method: getSeriesCategories($request, $response)
     - Query categories WHERE source_id=client.source_id AND category_type='series'
     - Apply filter
     - Generate favoris virtual categories for series (100000+)
     - Return favoris FIRST, then regular categories
   - Method: getSeries($request, $response)
     - Query series WHERE source_id=client.source_id
     - Filter by category_id if provided (including favoris)
     - Apply filter rules
     - Extract data from JSON field
     - Return series array (without episodes)
   - Method: getSeriesInfo($request, $response)
     - Get series_id from query
     - Verify client has access to series
     - Proxy request to source: action=get_series_info&series_id=X
     - Parse response and filter episodes if needed
     - Construct episode URLs: http://proxy-url/series/{username}/{password}/{episode_id}.{ext}
     - Return full series info with seasons and episodes
2. Routing:
   - GET /player_api.php?action=get_vod_categories
   - GET /player_api.php?action=get_vod_streams[&category_id=X]
   - GET /player_api.php?action=get_series_categories
   - GET /player_api.php?action=get_series[&category_id=X]
   - GET /player_api.php?action=get_series_info&series_id=X
3. Response formatting:
   - VOD streams include: stream_id, name, stream_icon, rating, added, category_id, container_extension, plot, cast, director, genre, year, etc.
   - Series include: series_id, name, cover, plot, cast, rating, genre, release_date, episode_run_time, etc.
   - Series info includes: seasons array, episodes object with season->episode structure

**Test Strategy:**

1. Test get_vod_categories returns all VOD categories
2. Test get_vod_categories includes favoris virtual categories first
3. Test get_vod_streams returns all VOD
4. Test get_vod_streams filters by category_id
5. Test get_vod_streams applies filter rules
6. Test get_vod_streams excludes adult content when enabled
7. Test get_series_categories returns series categories
8. Test get_series returns all series
9. Test get_series filters by category_id
10. Test get_series_info returns full series with episodes
11. Test VOD and series URLs constructed correctly
12. Integration test with IPTV player supporting VOD/series
