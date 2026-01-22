package org.anasoid.iptvorganizer.services.xtream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.xtream.XtreamAuthResponse;
import org.anasoid.iptvorganizer.dto.xtream.XtreamServerInfo;
import org.anasoid.iptvorganizer.dto.xtream.XtreamUserInfo;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/** Service for Xtream Codes API user operations */
@ApplicationScoped
public class XtreamUserService {

  private static final Logger LOGGER = Logger.getLogger(XtreamUserService.class.getName());

  private static final int DEFAULT_PAGINATION_LIMIT = 10000;
  private static final String DEFAULT_XTREAM_PASSWORD = "";

  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject FilterRepository filterRepository;
  @Inject XtreamClient xtreamClient;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject ContentFilterService contentFilterService;
  @Inject ObjectMapper objectMapper;

  /**
   * Authenticate client and return server/user info
   *
   * @param username Client username
   * @param password Client password
   * @param proxyUrl Base URL of this proxy server
   * @return Authentication response with server and user info
   * @throws RuntimeException if client not found or authentication fails
   */
  public XtreamAuthResponse authenticate(String username, String password, String proxyUrl) {
    // Validate inputs
    if (username == null || username.trim().isEmpty()) {
      throw new RuntimeException("Username is required");
    }

    // Find client by username
    Client client = clientRepository.findByUsername(username);
    if (client == null) {
      throw new RuntimeException("Client not found");
    }

    // Validate password
    if (password == null || !password.equals(client.getPassword())) {
      throw new RuntimeException("Invalid password");
    }

    // Check if client is active
    if (client.getIsActive() != null && !client.getIsActive()) {
      throw new RuntimeException("Client is inactive");
    }

    // Get source for this client
    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new RuntimeException("Source not found for client");
    }

    // Build response
    return buildAuthenticationResponse(client, source, proxyUrl);
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
    Client client = clientRepository.findByUsername(username);
    if (client == null) {
      LOGGER.warning("Client not found: " + username);
      throw new UnauthorizedException("Invalid credentials");
    }

    // Validate password
    if (!password.equals(client.getPassword())) {
      LOGGER.warning("Invalid password for client: " + username);
      throw new UnauthorizedException("Invalid credentials");
    }

    // Check if client is active
    if (client.getIsActive() != null && !client.getIsActive()) {
      LOGGER.warning("Client is inactive: " + username);
      throw new ForbiddenException("Client is inactive");
    }

    // Get source for this client
    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      LOGGER.warning("Source not found for client: " + username);
      throw new ForbiddenException("Source not configured for this client");
    }

    return new ClientAuthenticationResult(client, source);
  }

  /**
   * Get live categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getLiveCategories(Client client, Source source) {
    contentFilterService.initializeContext(client);
    try {
      List<Category> categories =
          contentFilterService.getAllowedCategories(
              source.getId(), StreamType.LIVE.getCategoryType(), DEFAULT_PAGINATION_LIMIT, 0);
      // Materialize all data to Maps BEFORE clearing context
      List<Map<?, ?>> categoryMaps = materializeCategories(categories);
      return new JsonStreamResult<>(categoryMaps.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
  }

  /**
   * Get VOD categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getVodCategories(Client client, Source source) {
    contentFilterService.initializeContext(client);
    try {
      List<Category> categories =
          contentFilterService.getAllowedCategories(
              source.getId(), StreamType.VOD.getCategoryType(), DEFAULT_PAGINATION_LIMIT, 0);
      // Materialize all data to Maps BEFORE clearing context
      List<Map<?, ?>> categoryMaps = materializeCategories(categories);
      return new JsonStreamResult<>(categoryMaps.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
  }

  /**
   * Get series categories for a client (with filtering applied)
   *
   * @param client The authenticated client
   * @param source The source
   * @return Stream result with lazy Iterator of categories
   */
  public JsonStreamResult<Map<?, ?>> getSeriesCategories(Client client, Source source) {
    contentFilterService.initializeContext(client);
    try {
      List<Category> categories =
          contentFilterService.getAllowedCategories(
              source.getId(), StreamType.SERIES.getCategoryType(), DEFAULT_PAGINATION_LIMIT, 0);
      LOGGER.info("Found " + categories.size() + " series categories before materialization");
      // Materialize all data to Maps BEFORE clearing context
      List<Map<?, ?>> categoryMaps = materializeCategories(categories);
      LOGGER.info("Materialized " + categoryMaps.size() + " series category maps");
      return new JsonStreamResult<>(categoryMaps.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
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
    contentFilterService.initializeContext(client);
    try {
      JsonStreamResult<Map<?, ?>> result =
          getFilteredStreamsByType(client, source, StreamType.LIVE, categoryId);
      // Materialize all streams to Maps BEFORE clearing context
      List<Map<?, ?>> materialized = new ArrayList<>();
      result.iterator().forEachRemaining(materialized::add);
      return new JsonStreamResult<>(materialized.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
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
    contentFilterService.initializeContext(client);
    try {
      JsonStreamResult<Map<?, ?>> result =
          getFilteredStreamsByType(client, source, StreamType.VOD, categoryId);
      // Materialize all streams to Maps BEFORE clearing context
      List<Map<?, ?>> materialized = new ArrayList<>();
      result.iterator().forEachRemaining(materialized::add);
      return new JsonStreamResult<>(materialized.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
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
    contentFilterService.initializeContext(client);
    try {
      JsonStreamResult<Map<?, ?>> result =
          getFilteredStreamsByType(client, source, StreamType.SERIES, categoryId);
      // Materialize all series to Maps BEFORE clearing context
      List<Map<?, ?>> materialized = new ArrayList<>();
      result.iterator().forEachRemaining(materialized::add);
      return new JsonStreamResult<>(materialized.iterator(), new AtomicLong(0), null);
    } finally {
      contentFilterService.clearContext();
    }
  }

  /**
   * Build authentication response with server and user info
   *
   * @param client The authenticated client
   * @param source The source
   * @param proxyUrl The proxy server URL
   * @return Built authentication response
   */
  private XtreamAuthResponse buildAuthenticationResponse(
      Client client, Source source, String proxyUrl) {
    try {
      // Try to fetch auth data from original source
      JsonStreamResult<Map<?, ?>> result = xtreamClient.getLiveCategories(source);
      // For auth endpoint, we just need the structure, so close the stream
      result.close();

      return buildFallbackAuthenticationResponse(client, proxyUrl);
    } catch (Exception ex) {
      LOGGER.warning(
          String.format(
              "Failed to fetch authentication from source %d: %s",
              source.getId(), ex.getMessage()));
      return buildFallbackAuthenticationResponse(client, proxyUrl);
    }
  }

  /**
   * Build fallback authentication response with basic info
   *
   * @param client The client
   * @param proxyUrl The proxy server URL
   * @return Built authentication response
   */
  private XtreamAuthResponse buildFallbackAuthenticationResponse(Client client, String proxyUrl) {
    XtreamServerInfo serverInfo = buildServerInfo(proxyUrl);

    XtreamUserInfo userInfo =
        XtreamUserInfo.builder()
            .username(client.getUsername())
            .password(DEFAULT_XTREAM_PASSWORD)
            .message("")
            .auth(1)
            .status(client.getIsActive() != null && client.getIsActive() ? "Active" : "Inactive")
            .isTrial("0")
            .activeConnections("0")
            .createdAt(System.currentTimeMillis() / 1000)
            .allowedOutputFormats(List.of("m3u8", "ts", "rtmp"))
            .build();

    if (client.getExpiryDate() != null) {
      userInfo.setExpDate(
          client.getExpiryDate().atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond());
    }

    return XtreamAuthResponse.builder().userInfo(userInfo).serverInfo(serverInfo).build();
  }

  /**
   * Build server info with proxy URL details
   *
   * @param proxyUrl The proxy URL
   * @return Built server info
   */
  private XtreamServerInfo buildServerInfo(String proxyUrl) {
    String host = "localhost";
    String port = "8080";
    String protocol = "http";
    String httpsPort = "443";

    // Parse proxy URL
    try {
      java.net.URL url = new java.net.URL(proxyUrl);
      host = url.getHost();
      protocol = url.getProtocol();
      port =
          String.valueOf(url.getPort() > 0 ? url.getPort() : (protocol.equals("https") ? 443 : 80));

      if (protocol.equals("https")) {
        httpsPort = port;
        port = "80";
      }
    } catch (Exception ex) {
      LOGGER.warning("Failed to parse proxy URL: " + ex.getMessage());
    }

    return XtreamServerInfo.builder()
        .url(host)
        .port(port)
        .httpsPort(httpsPort)
        .serverProtocol(protocol)
        .rtmpPort("1935")
        .timestampNow(System.currentTimeMillis() / 1000)
        .timeNow(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()))
        .build();
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
              return (Map<?, ?>) (Object) map;
            })
        .collect(Collectors.toList());
  }

  /**
   * Convert categories to Xtream JSON stream format
   *
   * @param categories List of categories
   * @return JsonStreamResult with categories in Xtream format
   */
  private JsonStreamResult<Map<?, ?>> convertCategoriesToJsonStream(List<Category> categories) {
    // Convert Category entities to Map format for Xtream API compatibility
    List<Map<?, ?>> categoryMaps = materializeCategories(categories);
    return new JsonStreamResult<>(categoryMaps.iterator(), new AtomicLong(0), null);
  }

  /**
   * Materialize streams to Maps (fully resolve lazy-loaded properties while context is active)
   *
   * @param streams List of stream entities
   * @return List of Maps with all data materialized
   */
  private List<Map<?, ?>> materializeStreams(List<? extends BaseStream> streams) {
    return streams.stream()
        .map(
            stream -> {
              Map<String, Object> map = new HashMap<>();
              // Materialize all properties immediately to avoid lazy loading during streaming
              map.put("num", stream.getNum());
              map.put("name", stream.getName());
              map.put("stream_id", stream.getExternalId());
              map.put("stream_icon", "");
              map.put("category_id", stream.getCategoryId());
              map.put(
                  "added", stream.getAddedDate() != null ? stream.getAddedDate().toString() : "");
              map.put("is_adult", stream.getIsAdult() ? "1" : "0");
              map.put("category_ids", stream.getCategoryIds());

              // Include raw JSON data if available
              if (stream.getData() != null && !stream.getData().isEmpty()) {
                try {
                  Map<String, Object> rawData = objectMapper.readValue(stream.getData(), Map.class);
                  // Merge raw data with our standardized fields
                  rawData.forEach(
                      (key, value) -> {
                        if (!map.containsKey(key)) {
                          map.put(key, value);
                        }
                      });
                } catch (Exception e) {
                  LOGGER.warning(
                      "Failed to parse stream data for stream "
                          + stream.getExternalId()
                          + ": "
                          + e.getMessage());
                }
              }

              return (Map<?, ?>) (Object) map;
            })
        .collect(Collectors.toList());
  }

  /**
   * Convert streams to Xtream JSON stream format
   *
   * @param streams List of stream entities from database
   * @return JsonStreamResult with streams in Xtream format
   */
  private JsonStreamResult<Map<?, ?>> convertStreamsToJsonStream(
      List<? extends BaseStream> streams) {
    // Convert stream entities to Map format for Xtream API compatibility
    // Using streams stored in database instead of raw API data
    List<Map<?, ?>> streamMaps = materializeStreams(streams);
    return new JsonStreamResult<>(streamMaps.iterator(), new AtomicLong(0), null);
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
    // Get raw streams from upstream
    JsonStreamResult<Map<?, ?>> rawStreams = getStreamsByType(source, type, categoryId);

    // Build category cache for filtering
    Map<Integer, Category> categoryCache =
        buildCategoryCache(source.getId(), type.getCategoryType());

    // Apply filtering to stream iterator
    return applyStreamFiltering(rawStreams, categoryCache);
  }

  /**
   * Apply filtering to streamed results
   *
   * @param rawStreams Raw stream results from Xtream API
   * @param categoryCache Cached categories for lookups
   * @return Filtered stream results
   */
  private JsonStreamResult<Map<?, ?>> applyStreamFiltering(
      JsonStreamResult<Map<?, ?>> rawStreams, Map<Integer, Category> categoryCache) {
    // For now, return as-is - filtering would be applied per-stream
    // in a lazy iterator wrapper if needed
    return rawStreams;
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
   * on-demand from database instead of making real-time calls to upstream API.
   *
   * @param source The source
   * @param type The stream type
   * @param categoryId Optional category filter (not currently implemented)
   * @return Stream result with lazy Iterator
   */
  private JsonStreamResult<Map<?, ?>> getStreamsByType(
      Source source, StreamType type, Long categoryId) {
    List<? extends BaseStream> streams = null;

    switch (type) {
      case LIVE:
        streams = liveStreamService.findBySourceId(source.getId());
        break;
      case VOD:
        streams = vodStreamService.findBySourceId(source.getId());
        break;
      case SERIES:
        streams = seriesService.findBySourceId(source.getId());
        break;
      default:
        throw new IllegalArgumentException("Unknown stream type: " + type);
    }

    // Convert stream entities to Xtream API format and wrap in JsonStreamResult
    return convertStreamsToJsonStream(streams);
  }
}
