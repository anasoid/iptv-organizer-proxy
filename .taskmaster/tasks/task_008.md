# Task ID: 8

**Title:** Xtream Codes API Implementation - Live Streams & Categories

**Status:** pending

**Dependencies:** 7

**Priority:** high

**Description:** Implement player_api.php endpoint handler for live categories and streams with filtering based on client's source and filter assignment

**Details:**

1. Create Slim application: `public/index.php`
   - Initialize Slim App
   - Add routing for /player_api.php
   - Add ClientAuthMiddleware globally
   - Add error handling middleware
   - Add CORS middleware
2. Create `src/Controllers/XtreamController.php`:
   - Method: authenticate($request, $response)
     - Return server_info and user_info JSON
     - server_info: url (proxy URL), port, https_port
     - user_info: username, status, exp_date, max_connections
   - Method: getLiveCategories($request, $response)
     - Get client from request attributes
     - Query categories table WHERE source_id=client.source_id AND category_type='live'
     - Apply filter if client has filter assigned
     - Generate favoris virtual categories (100000+) from filter
     - Return favoris categories FIRST, then regular categories
     - Format as Xtream categories JSON array
   - Method: getLiveStreams($request, $response)
     - Get client and optional category_id parameter
     - Query live_streams WHERE source_id=client.source_id
     - If category_id provided: filter by category_id or favoris category
     - If category_id >= 100000: apply favoris matching from filter
     - Apply client's filter (include/exclude rules)
     - If client.hide_adult_content=1: exclude is_adult=1 streams
     - Extract stream data from JSON 'data' field
     - Construct stream_url: http://proxy-url/live/{client.username}/{client.password}/{stream_id}.m3u8
     - Add stream_url to each stream object
     - Return streams array in Xtream format
3. Routing:
   - GET /player_api.php (no action) → authenticate()
   - GET /player_api.php?action=get_live_categories → getLiveCategories()
   - GET /player_api.php?action=get_live_streams → getLiveStreams()
   - GET /player_api.php?action=get_live_streams&category_id=X → getLiveStreams()
4. Response formatting:
   - Return JSON with proper Content-Type
   - Handle empty results gracefully
   - Log all requests to connection_logs

**Test Strategy:**

1. Test authenticate endpoint returns correct JSON
2. Test get_live_categories returns all categories for source
3. Test get_live_categories includes favoris virtual categories first
4. Test get_live_streams returns all streams
5. Test get_live_streams filters by category_id
6. Test get_live_streams applies filter rules correctly
7. Test get_live_streams excludes adult content when hide_adult_content=1
8. Test stream URLs constructed correctly with proxy domain
9. Test authentication required for all endpoints
10. Integration test with IPTV player (TiviMate, VLC)
