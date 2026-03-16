package org.anasoid.iptvorganizer.services.xtream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/** Service for Xtream Codes API user operations */
@Slf4j
@ApplicationScoped
public class XtreamUserService {

  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject ContentFilterService contentFilterService;
  @Inject ObjectMapper objectMapper;
  @Inject HttpStreamingService httpStreamingService;
  @Inject XtreamClient xtreamClient;

  /**
   * Authenticate client and return server/user info as JSON Map
   *
   * @param username Client username
   * @param password Client password
   * @return Authentication response as Map with server and user info
   * @throws RuntimeException if client not found or authentication fails
   */
  public Map<String, Object> authenticate(
      String username, String password, HttpRequestDto requestDto) {

    // Find client by username
    Client client = clientRepository.findByUsernameAndPassword(username, password);

    // Get source for this client
    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new RuntimeException("Source not found for client");
    }

    // Try to fetch from upstream, fallback on error

    Map<String, Object> authData = fetchAuthenticationFromUpstream(source, requestDto);

    // Replace user credentials with client credentials
    replaceUserInfo(authData, client);

    return authData;
  }

  /**
   * Authenticate and validate client for streaming access
   *
   * <p>Centralized validation logic that performs all required checks: - Client exists and password
   * is correct - Client is active - Source is available
   *
   * <p>Usage in controllers:
   *
   * <pre>
   * try {
   *   ClientAuthenticationResult result = xtreamUserService.authenticateAndValidateClient(username, password);
   *   Client client = result.getClient();
   *   Source source = result.getSource();
   *   // proceed with logic
   * } catch (UnauthorizedException ex) {
   *   return Response.status(Response.Status.UNAUTHORIZED).build();
   * } catch (ForbiddenException ex) {
   *   return Response.status(Response.Status.FORBIDDEN).build();
   * }
   * </pre>
   *
   * @param username Client username
   * @param password Client password
   * @return Result containing authenticated client and source
   * @throws UnauthorizedException if credentials are invalid or client not found
   * @throws ForbiddenException if client is inactive
   */
  public ClientAuthenticationResult authenticateAndValidateClient(
      String username, String password) {
    // Validate inputs
    if (username == null || username.trim().isEmpty()) {
      throw new UnauthorizedException("Username is required");
    }

    if (password == null) {
      throw new UnauthorizedException("Password is required");
    }

    // Find client by username
    Client client = clientRepository.findByUsernameAndPassword(username, password);

    // Get source for this client
    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      log.warn("Source not found for client: {}", username);
      throw new ForbiddenException("Source not configured for this client");
    }

    return new ClientAuthenticationResult(client, source);
  }

  /**
   * Get categories for a client by type (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @param type The stream type
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getCategories(Client client, Source source, StreamType type) {
    FilterContext context = contentFilterService.buildFilterContext(client);
    List<Category> categories =
        contentFilterService.getAllowedCategories(context, source.getId(), type.getCategoryType());
    // Materialize all data to Maps
    List<Map<?, ?>> categoryMaps = materializeCategories(categories);
    return new JsonStreamResult<>(categoryMaps.iterator(), new AtomicLong(0), null);
  }

  /**
   * Get live categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getLiveCategories(Client client, Source source) {
    return getCategories(client, source, StreamType.LIVE);
  }

  /**
   * Get VOD categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getVodCategories(Client client, Source source) {
    return getCategories(client, source, StreamType.VOD);
  }

  /**
   * Get series categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getSeriesCategories(Client client, Source source) {
    return getCategories(client, source, StreamType.SERIES);
  }

  /**
   * Get live streams for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @param categoryId Optional category filter
   * @return Stream result with lazy Iterator of streams
   */
  public JsonStreamResult<Map<?, ?>> getLiveStreams(Client client, Source source, Long categoryId) {
    return getFilteredStreamsByType(client, source, StreamType.LIVE, categoryId);
  }

  /**
   * Get VOD streams for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @param categoryId Optional category filter
   * @return Stream result with lazy Iterator of streams
   */
  public JsonStreamResult<Map<?, ?>> getVodStreams(Client client, Source source, Long categoryId) {

    return getFilteredStreamsByType(client, source, StreamType.VOD, categoryId);
  }

  /**
   * Get series for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @param categoryId Optional category filter
   * @return Stream result with lazy Iterator of series
   */
  public JsonStreamResult<Map<?, ?>> getSeries(Client client, Source source, Long categoryId) {

    return getFilteredStreamsByType(client, source, StreamType.SERIES, categoryId);
  }

  /**
   * Get detailed series info with access control (proxy passthrough).
   *
   * @param client The authenticated client
   * @param source The source
   * @return HttpStreamingResponse with raw upstream response
   * @throws NotFoundException if series not in database
   * @throws ForbiddenException if client access denied
   */
  public HttpStreamingResponse getLiveSimpleDataTableRaw(
      Client client, Source source, Integer streamId) {
    // Load series from database for filtering check
    LiveStream stream = liveStreamService.findBySourceAndStreamId(source.getId(), streamId);
    if (stream == null) {
      log.warn("stream {} not found in database for source {}", streamId, source.getName());
      throw new NotFoundException("Series not found");
    }
    checkStreamAccess(stream, client, source);
    // Fetch raw response from upstream (proxy passthrough)
    return xtreamClient.getLiveSimpleDataTableRaw(client, source, streamId);
  }

  /**
   * detailed series info with access control (proxy passthrough).
   *
   * @param client The authenticated client
   * @param source The source
   * @param seriesId The series ID to fetch info for
   * @return HttpStreamingResponse with raw upstream response
   * @throws NotFoundException if series not in database
   * @throws ForbiddenException if client access denied
   */
  public HttpStreamingResponse getSeriesInfoRaw(Client client, Source source, Integer seriesId) {
    // Load series from database for filtering check
    Series stream = seriesService.findBySourceAndStreamId(source.getId(), seriesId);

    if (stream == null) {
      log.warn("Series {} not found in database for source {}", seriesId, source.getName());
      throw new NotFoundException("Series not found");
    }

    checkStreamAccess(stream, client, source);
    // Fetch raw response from upstream (proxy passthrough)
    return xtreamClient.getSeriesInfoRaw(client, source, seriesId);
  }

  private void checkStreamAccess(BaseStream stream, Client client, Source source) {

    // Load category for filtering
    Category category = null;
    if (stream.getCategoryId() != null) {
      category =
          categoryService.findBySourceAndCategoryId(
              source.getId(), stream.getCategoryId(), stream.getStreamType().getCategoryType());
    }

    // Build filtering context and check access
    FilterContext context = contentFilterService.buildFilterContext(client);
    Map<String, Boolean> categoryMatchCache = new HashMap<>();

    if (!contentFilterService.shouldIncludeStream(context, stream, category, categoryMatchCache)) {
      log.warn("Client {} denied access to series {}", client.getUsername(), stream);
      throw new ForbiddenException("Access denied to this series");
    }
  }

  /**
   * Fetch authentication data from upstream source
   *
   * @param source The source with credentials
   * @return Authentication data as Map with user_info and server_info
   */
  private Map<String, Object> fetchAuthenticationFromUpstream(
      Source source, HttpRequestDto request) {
    // Build upstream URL with source credentials
    String upstreamUrl =
        String.format(
            "%s/player_api.php?username=%s&password=%s",
            source.getUrl().replaceAll("/$", ""), source.getUsername(), source.getPassword());

    // Fetch from upstream
    HttpOptions options = HttpOptions.builder().timeout(30000L).maxRetries(1).build();

    Map<String, Object> authData =
        httpStreamingService.fetchJsonObject(upstreamUrl, options, source);

    // Validate structure
    if (!authData.containsKey("user_info") || !authData.containsKey("server_info")) {
      throw new RuntimeException("Invalid authentication response from upstream");
    }

    // Replace server_info with proxy details
    replaceServerInfo(authData, request.getUrl());

    return authData;
  }

  /**
   * Replace server_info fields with proxy details
   *
   * @param authData The authentication data Map
   * @param proxyUrl The proxy server URL
   */
  @SuppressWarnings("unchecked")
  private void replaceServerInfo(Map<String, Object> authData, String proxyUrl) {
    try {
      java.net.URL url = URI.create(proxyUrl).toURL();
      String scheme = url.getProtocol();
      String host = url.getHost();
      int port = url.getPort() > 0 ? url.getPort() : (scheme.equals("https") ? 443 : 80);

      Map<String, Object> serverInfo = (Map<String, Object>) authData.get("server_info");

      // Replace URL with proxy hostname
      serverInfo.put("url", host);
      serverInfo.put("server_protocol", scheme);

      // Set ports based on protocol
      if ("https".equals(scheme)) {
        serverInfo.put("https_port", String.valueOf(port));
        serverInfo.put("port", "80");
      } else {
        serverInfo.put("port", String.valueOf(port));
        serverInfo.put("https_port", "443");
      }

    } catch (Exception ex) {
      log.warn("Failed to parse proxy URL for server info replacement", ex);
    }
  }

  /**
   * Replace user_info fields with client credentials
   *
   * @param authData The authentication data Map
   * @param client The client
   */
  @SuppressWarnings("unchecked")
  private void replaceUserInfo(Map<String, Object> authData, Client client) {
    Map<String, Object> userInfo = (Map<String, Object>) authData.get("user_info");

    // Replace with client credentials (not source credentials)
    userInfo.put("username", client.getUsername());
    userInfo.put("password", client.getPassword()); // Empty string
  }

  /**
   * Convert a single stream to Xtream Map format (lazy conversion).
   *
   * <p>Maps a BaseStream entity to a Map<String, Object> with Xtream API fields. This is called
   * once per item during iteration, not for all items upfront.
   *
   * @param stream The stream entity to convert
   * @return Map with Xtream API format fields
   */
  private Map<?, ?> convertStreamToMap(BaseStream stream) {
    Map<String, Object> map = new HashMap<>();
    // Materialize properties one at a time for this stream
    map.put("num", stream.getNum());
    map.put("name", stream.getName());
    map.put("stream_id", stream.getExternalId());
    map.put("stream_icon", "");
    map.put("category_id", stream.getCategoryId());
    map.put("added", stream.getAddedDate() != null ? stream.getAddedDate().toString() : "");
    map.put("is_adult", stream.getIsAdult() ? "1" : "0");
    map.put("category_ids", stream.getCategoryIds());

    // Include raw JSON data if available
    if (stream.getData() != null && !stream.getData().isEmpty()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawData = objectMapper.readValue(stream.getData(), Map.class);
        // Merge raw data with our standardized fields
        rawData.forEach(
            (key, value) -> {
              if (!map.containsKey(key)) {
                map.put(key, value);
              }
            });
      } catch (Exception e) {
        log.warn("Failed to parse stream data for stream {}", stream.getExternalId(), e);
      }
    }

    return map;
  }

  /**
   * Materialize categories to Maps (fully resolve lazy-loaded properties while context is active)
   *
   * @param categories List of categories
   * @return List of Maps with all data materialized
   */
  private List<Map<?, ?>> materializeCategories(List<Category> categories) {
    return categories.stream()
        .map(
            cat -> {
              Map<String, Object> map = new HashMap<>();
              // Materialize all properties immediately to avoid lazy loading during streaming
              map.put("category_id", cat.getExternalId());
              map.put("category_name", cat.getName());
              map.put("parent_id", cat.getParentId());
              return (Map<?, ?>) map;
            })
        .collect(Collectors.toList());
  }

  /**
   * Get filtered streams by type
   *
   * @param client The authenticated client
   * @param source The source
   * @param type The stream type
   * @param categoryId Optional category filter
   * @return Stream result with filtered streams
   */
  private JsonStreamResult<Map<?, ?>> getFilteredStreamsByType(
      Client client, Source source, StreamType type, Long categoryId) {
    FilterContext context = contentFilterService.buildFilterContext(client);
    // Get raw streams from upstream
    JsonStreamResult<Map<?, ?>> rawStreams = getStreamsByType(source, type, categoryId);

    // Build category cache for filtering
    Map<Integer, Category> categoryCache =
        buildCategoryCache(source.getId(), type.getCategoryType());

    // Apply filtering to stream iterator
    return applyStreamFiltering(context, rawStreams, categoryCache);
  }

  /**
   * Apply filtering to streamed results
   *
   * @param context The filtering context
   * @param rawStreams Raw stream results from Xtream API
   * @param categoryCache Cached categories for lookups
   * @return Filtered stream results
   */
  private JsonStreamResult<Map<?, ?>> applyStreamFiltering(
      FilterContext context,
      JsonStreamResult<Map<?, ?>> rawStreams,
      Map<Integer, Category> categoryCache) {
    // If no filtering needed, return as-is
    if (context == null) {
      return rawStreams;
    }

    // Create lazy filtering iterator that filters streams during iteration
    Iterator<Map<?, ?>> filteringIterator =
        new FilteringIterator(rawStreams.iterator(), context, categoryCache);

    // Wrap in new JsonStreamResult with same close handler
    return new JsonStreamResult<>(filteringIterator, new AtomicLong(0), rawStreams::close);
  }

  /**
   * Build category cache for efficient filtering
   *
   * @param sourceId ID of source
   * @param type Stream type
   * @return Map of category ID → Category
   */
  private Map<Integer, Category> buildCategoryCache(Long sourceId, String type) {
    List<Category> categories = categoryService.findBySourceAndType(sourceId, type);
    Map<Integer, Category> cache = new HashMap<>();
    for (Category cat : categories) {
      cache.put(cat.getExternalId(), cat);
    }
    return cache;
  }

  /**
   * Get streams by type from local database (synchronized from Xtream API). Streams are loaded
   * on-demand from database using lazy iterator instead of loading all into memory.
   *
   * @param source The source
   * @param type The stream type
   * @param categoryId Optional category filter (not currently implemented)
   * @return Stream result with lazy Iterator
   */
  private JsonStreamResult<Map<?, ?>> getStreamsByType(
      Source source, StreamType type, Long categoryId) {
    final Iterator<? extends BaseStream> streamIterator =
        switch (type) {
          case LIVE -> liveStreamService.streamBySourceId(source.getId());
          case VOD -> vodStreamService.streamBySourceId(source.getId());
          case SERIES -> seriesService.streamBySourceId(source.getId());
          default -> throw new IllegalArgumentException("Unknown stream type: " + type);
        };

    // Wrap with lazy Map conversion (single item at a time)
    Iterator<Map<?, ?>> mapIterator = new MappingIterator(streamIterator);

    return new JsonStreamResult<>(
        mapIterator, new AtomicLong(0), () -> closeIterator(streamIterator));
  }

  /**
   * Close iterator if it implements Closeable
   *
   * @param iterator The iterator to close
   */
  private void closeIterator(Iterator<?> iterator) {
    if (iterator instanceof AutoCloseable) {
      try {
        ((AutoCloseable) iterator).close();
      } catch (Exception e) {
        log.warn("Failed to close iterator", e);
      }
    }
  }

  /**
   * Lazy mapping iterator that converts BaseStream to Map on-demand.
   *
   * <p>Single item conversion only - materializes one stream to Map at a time while
   * ContentFilterService context is active.
   */
  private class MappingIterator implements Iterator<Map<?, ?>> {
    private final Iterator<? extends BaseStream> delegate;

    MappingIterator(Iterator<? extends BaseStream> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Map<?, ?> next() {
      BaseStream stream = delegate.next();
      return convertStreamToMap(stream); // Single item conversion
    }
  }

  /**
   * Lazy filtering iterator that filters streams using FilterContext.
   *
   * <p>Uses lookahead pattern to process streams one-at-a-time without materializing entire list.
   * Returns original Maps for streams that pass filtering checks.
   */
  private class FilteringIterator implements Iterator<Map<?, ?>> {
    private final Iterator<Map<?, ?>> delegate;
    private final FilterContext context;
    private final Map<Integer, Category> categoryCache;
    Map<String, Boolean> categoryMatchCache = new HashMap<>();
    private Map<?, ?> nextItem = null;
    private boolean hasNextCached = false;

    FilteringIterator(
        Iterator<Map<?, ?>> delegate, FilterContext context, Map<Integer, Category> categoryCache) {
      this.delegate = delegate;
      this.context = context;
      this.categoryCache = categoryCache;
    }

    @Override
    public boolean hasNext() {
      if (!hasNextCached) {
        hasNextCached = true;
        nextItem = advanceToNext();
      }
      return nextItem != null;
    }

    @Override
    public Map<?, ?> next() {
      if (!hasNext()) {
        throw new java.util.NoSuchElementException();
      }
      hasNextCached = false;
      Map<?, ?> result = nextItem;
      nextItem = null;
      return result;
    }

    /** Advance iterator until finding a stream that passes filtering. */
    private Map<?, ?> advanceToNext() {
      while (delegate.hasNext()) {
        Map<?, ?> item = delegate.next();
        BaseStream stream = extractStreamFromMap(item);

        // Look up category from cache (may be null)
        Category category = categoryCache.get(stream.getCategoryId());

        // Check if stream passes filtering
        if (contentFilterService.shouldIncludeStream(
            context, stream, category, categoryMatchCache)) {
          return item; // Return original Map
        }
      }
      return null; // No more streams
    }

    /**
     * Extract stream information from Map and build temporary BaseStream for filtering.
     *
     * @param map The Map containing stream data
     * @return Temporary BaseStream with extracted fields
     */
    private BaseStream extractStreamFromMap(Map<?, ?> map) {
      // Build temporary BaseStream using builder pattern
      var builder = LiveStream.builder();

      // Extract and convert Map fields
      if (map.containsKey("stream_id")) {
        builder.externalId(toInteger(map.get("stream_id")));
      }
      if (map.containsKey("name")) {
        builder.name(toString(map.get("name")));
      }
      if (map.containsKey("category_id")) {
        builder.categoryId(toInteger(map.get("category_id")));
      }
      if (map.containsKey("is_adult")) {
        builder.isAdult(toBoolean(map.get("is_adult")));
      }
      if (map.containsKey("allow_deny")) {
        builder.allowDeny(BaseStream.AllowDenyStatus.fromValue(toString(map.get("allow_deny"))));
      }
      if (map.containsKey("labels")) {
        builder.labels(toString(map.get("labels")));
      }

      return builder.build();
    }

    /** Convert Object to Integer, handling various types. */
    private Integer toInteger(Object obj) {
      switch (obj) {
        case null -> {
          return null;
        }
        case Integer i -> {
          return i;
        }
        case Number number -> {
          return number.intValue();
        }
        case String s -> {
          try {
            return Integer.parseInt(s);
          } catch (NumberFormatException e) {
            return null;
          }
        }
        default -> {}
      }
      return null;
    }

    /** Convert Object to String. */
    private String toString(Object obj) {
      return obj == null ? null : obj.toString();
    }

    /** Convert Object to Boolean, handling various types ("1"/"0", true/false, numeric). */
    private Boolean toBoolean(Object obj) {
      return switch (obj) {
        case null -> null;
        case Boolean b -> b;
        case String str -> "1".equals(str) || "true".equalsIgnoreCase(str);
        case Number number -> number.intValue() != 0;
        default -> false;
      };
    }
  }
}
