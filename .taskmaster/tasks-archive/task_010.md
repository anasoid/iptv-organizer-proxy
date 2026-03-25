# Task ID: 10

**Title:** Stream URL Proxy & EPG Proxying

**Status:** pending

**Dependencies:** 9

**Priority:** high

**Description:** Implement stream URL redirection/proxying to source servers and EPG data proxying with on-the-fly filtering

**Details:**

1. Stream URL routing in Slim:
   - GET /live/{username}/{password}/{stream_id}.{ext} → proxyLiveStream()
   - GET /movie/{username}/{password}/{stream_id}.{ext} → proxyVodStream()
   - GET /series/{username}/{password}/{stream_id}.{ext} → proxySeriesStream()
   - {ext} can be any extension: m3u8, ts, mp4, mkv, avi, flv, etc.
2. Create `src/Controllers/ProxyController.php`:
   - Method: proxyLiveStream($request, $response, $args)
     - Extract username, password, stream_id, extension from route
     - Authenticate client (username/password)
     - Verify client has access to stream_id (check source, filter, adult content)
     - Load source credentials
     - Construct source URL: http://source-server/live/source_user/source_pass/{stream_id}.{ext}
     - Option 1: Redirect (302) to source URL
     - Option 2: Reverse proxy using Guzzle stream
     - Return response
   - Method: proxyVodStream() - same logic for VOD
   - Method: proxySeriesStream() - same logic for series
3. EPG proxying:
   - Method: getSimpleDataTable($request, $response)
     - Extract stream_id from query
     - Authenticate client
     - Verify client has access to stream
     - Proxy request to source: player_api.php?action=get_simple_data_table&stream_id=X
     - Return EPG data without filtering (direct proxy)
   - Method: getShortEpg($request, $response)
     - Similar to getSimpleDataTable with limit parameter
   - Method: getXmltv($request, $response)
     - Extract optional stream_id from query
     - If stream_id provided: proxy directly without filtering
     - If no stream_id (full XMLTV): proxy and filter on-the-fly
       - Fetch full XMLTV from source
       - Parse XML
       - Filter <programme> and <channel> elements to only client's accessible streams
       - Return filtered XML
4. Nginx configuration for URL rewriting:
   - Create nginx.conf template
   - Rewrite rules to pass extensions to PHP
   - FastCGI configuration for PHP
   - Proxy pass configuration for reverse proxy
5. Performance optimization:
   - Use HTTP redirect (302) for streams (faster, offloads bandwidth)
   - Cache EPG data for short duration (5-10 minutes)
   - Use Guzzle streaming for large responses

**Test Strategy:**

1. Test live stream URL redirects to correct source URL
2. Test VOD stream URL redirects correctly
3. Test series stream URL redirects correctly
4. Test client authentication required for stream access
5. Test client cannot access streams from other sources
6. Test filter and adult content rules enforced for streams
7. Test EPG get_simple_data_table proxies correctly
8. Test get_short_epg proxies with limit parameter
9. Test XMLTV with stream_id proxies directly
10. Test full XMLTV filters channels on-the-fly
11. Integration test: play stream in VLC via proxy
12. Test various file extensions work (m3u8, ts, mp4, mkv)
