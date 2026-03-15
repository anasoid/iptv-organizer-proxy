package org.anasoid.iptvorganizer.utils.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;

/**
 * Client for Xtream Codes API interactions.
 *
 * <p>Encapsulates all Xtream Codes API endpoint calls and URL construction logic. Provides
 * streaming support for categories and streams. All HTTP calls are delegated to
 * HttpStreamingService.
 */
@Slf4j
@ApplicationScoped
public class XtreamClient {

  private static final long DEFAULT_TIMEOUT_MS = 30000;
  private static final int DEFAULT_MAX_RETRIES = 1;

  @Inject HttpStreamingService httpStreamingService;

  /**
   * Fetch live categories from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getLiveCategories(Source source) {
    return fetchCategories(source, StreamType.LIVE);
  }

  /**
   * Fetch VOD categories from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getVodCategories(Source source) {
    return fetchCategories(source, StreamType.VOD);
  }

  /**
   * Fetch series categories from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getSeriesCategories(Source source) {
    return fetchCategories(source, StreamType.SERIES);
  }

  /**
   * Fetch live streams from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getLiveStreams(Source source) {
    return fetchStreams(source, StreamType.LIVE);
  }

  /**
   * Fetch VOD streams from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getVodStreams(Source source) {
    return fetchStreams(source, StreamType.VOD);
  }

  /**
   * Fetch series from Xtream API using lazy Iterator-based streaming.
   *
   * @param source The source configuration containing credentials and URL
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> getSeries(Source source) {
    return fetchStreams(source, StreamType.SERIES);
  }

  /**
   * Fetch detailed series info as raw streaming response (proxy passthrough).
   *
   * @param source The source configuration containing credentials and URL
   * @param seriesId The series ID to fetch info for
   * @return HttpStreamingResponse with raw response stream
   */
  public HttpStreamingResponse getSeriesInfoRaw(Source source, Integer seriesId) {
    String url = buildApiUrlWithParam(source, "get_series_info", "series_id", seriesId);

    log.info(
        "Fetching series info (proxy) for series_id={} from source: {}",
        seriesId,
        source.getName());

    try {
      return httpStreamingService.streamHttpWithHeaders(
          url, createDefaultHttpOptions(), null, source);
    } catch (Exception ex) {
      log.error(
          "Failed to fetch series info for series_id={} from source {}: {}",
          seriesId,
          source.getName(),
          ex.getMessage());
      throw ex;
    }
  }

  /**
   * Fetch detailed series info as raw streaming response (proxy passthrough).
   *
   * @param source The source configuration containing credentials and URL
   * @param streamId The series ID to fetch info for
   * @return HttpStreamingResponse with raw response stream
   */
  public HttpStreamingResponse getLiveSimpleDataTableRaw(Source source, Integer streamId) {
    String url = buildApiUrlWithParam(source, "get_simple_data_table", "stream_id", streamId);

    log.info(
            "Fetching simple datatable info (proxy) for stream_id={} from source: {}",
            streamId,
            source.getName());

    try {
      return httpStreamingService.streamHttpWithHeaders(
              url, createDefaultHttpOptions(), null, source);
    } catch (Exception ex) {
      log.error(
              "Failed to fetch simple datatable info for stream_id={} from source {}: {}",
              streamId,
              source.getName(),
              ex.getMessage());
      throw ex;
    }
  }

  /**
   * Generic method to fetch categories for a given stream type using lazy streaming.
   *
   * @param source The source configuration
   * @param type The stream type (LIVE, VOD, SERIES)
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  private JsonStreamResult<Map<?, ?>> fetchCategories(Source source, StreamType type) {
    String action = type.getCategoryAction();
    String url = buildApiUrl(source, action);

    log.info(
        String.format(
            "Fetching %s categories for source: %s", type.getStreamTypeName(), source.getName()));

    try {
      return httpStreamingService.streamJsonArray(url, createDefaultHttpOptions(), source);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to fetch %s categories from source %s: %s",
              type.getStreamTypeName(), source.getName(), ex.getMessage()));
      throw ex;
    }
  }

  /**
   * Generic method to fetch streams for a given stream type using lazy streaming.
   *
   * @param source The source configuration
   * @param type The stream type (LIVE, VOD, SERIES)
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  private JsonStreamResult<Map<?, ?>> fetchStreams(Source source, StreamType type) {
    String action = type.getStreamAction();
    String url = buildApiUrl(source, action);

    log.info(
        String.format("Fetching %s for source: %s", type.getStreamTypeName(), source.getName()));

    try {
      return httpStreamingService.streamJsonArray(url, createDefaultHttpOptions(), source);
    } catch (Exception ex) {
      log.error(
          String.format(
              "Failed to fetch %s from source %s: %s",
              type.getStreamTypeName(), source.getName(), ex.getMessage()));
      throw ex;
    }
  }

  /**
   * Build Xtream Codes API URL with authentication parameters.
   *
   * <p>Format: {baseUrl}/player_api.php?action={action}&username={username}&password={password}
   *
   * @param source The source configuration containing base URL and credentials
   * @param action The Xtream API action (e.g., "get_live_streams")
   * @return Fully constructed API URL
   */
  private String buildApiUrl(Source source, String action) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    return String.format(
        "%s/player_api.php?action=%s&username=%s&password=%s",
        baseUrl, action, source.getUsername(), source.getPassword());
  }

  /**
   * Build Xtream Codes API URL with authentication and additional parameter.
   *
   * <p>Format:
   * {baseUrl}/player_api.php?action={action}&username={username}&password={password}&{paramName}={paramValue}
   *
   * @param source The source configuration containing base URL and credentials
   * @param action The Xtream API action (e.g., "get_series_info")
   * @param paramName Additional parameter name (e.g., "series_id")
   * @param paramValue Additional parameter value
   * @return Fully constructed API URL
   */
  private String buildApiUrlWithParam(
      Source source, String action, String paramName, Object paramValue) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    return String.format(
        "%s/player_api.php?action=%s&username=%s&password=%s&%s=%s",
        baseUrl, action, source.getUsername(), source.getPassword(), paramName, paramValue);
  }

  /**
   * Create default HTTP options for Xtream API calls.
   *
   * @return Configured HttpOptions with sensible defaults
   */
  private HttpOptions createDefaultHttpOptions() {
    return HttpOptions.builder()
        .timeout(DEFAULT_TIMEOUT_MS)
        .maxRetries(DEFAULT_MAX_RETRIES)
        .build();
  }
}
