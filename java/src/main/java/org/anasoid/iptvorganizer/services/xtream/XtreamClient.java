package org.anasoid.iptvorganizer.services.xtream;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.services.streaming.HttpStreamingService;

/**
 * Client for Xtream Codes API interactions.
 *
 * <p>Encapsulates all Xtream Codes API endpoint calls and URL construction logic. Provides reactive
 * streaming support for categories and streams. All HTTP calls are delegated to
 * HttpStreamingService.
 */
@ApplicationScoped
public class XtreamClient {

  private static final Logger LOGGER = Logger.getLogger(XtreamClient.class.getName());

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_MAX_RETRIES = 3;

  @Inject HttpStreamingService httpStreamingService;

  /**
   * Fetch live categories from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of category data maps
   */
  public Multi<Map> getLiveCategories(Source source) {
    return fetchCategories(source, StreamType.LIVE);
  }

  /**
   * Fetch VOD categories from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of category data maps
   */
  public Multi<Map> getVodCategories(Source source) {
    return fetchCategories(source, StreamType.VOD);
  }

  /**
   * Fetch series categories from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of category data maps
   */
  public Multi<Map> getSeriesCategories(Source source) {
    return fetchCategories(source, StreamType.SERIES);
  }

  /**
   * Fetch live streams from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of live stream data maps
   */
  public Multi<Map> getLiveStreams(Source source) {
    return fetchStreams(source, StreamType.LIVE);
  }

  /**
   * Fetch VOD streams from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of VOD stream data maps
   */
  public Multi<Map> getVodStreams(Source source) {
    return fetchStreams(source, StreamType.VOD);
  }

  /**
   * Fetch series from Xtream API.
   *
   * @param source The source configuration containing credentials and URL
   * @return Multi stream of series data maps
   */
  public Multi<Map> getSeries(Source source) {
    return fetchStreams(source, StreamType.SERIES);
  }

  /**
   * Generic method to fetch categories for a given stream type.
   *
   * @param source The source configuration
   * @param type The stream type (LIVE, VOD, SERIES)
   * @return Multi stream of category data maps
   */
  private Multi<Map> fetchCategories(Source source, StreamType type) {
    String action = type.getCategoryAction();
    String url = buildApiUrl(source, action);

    LOGGER.info(
        String.format(
            "Fetching %s categories for source: %s", type.getStreamTypeName(), source.getName()));

    return httpStreamingService
        .streamJson(url, Map.class, createDefaultHttpOptions())
        .onFailure()
        .invoke(
            ex ->
                LOGGER.severe(
                    String.format(
                        "Failed to fetch %s categories from source %s: %s",
                        type.getStreamTypeName(), source.getName(), ex.getMessage())));
  }

  /**
   * Generic method to fetch streams for a given stream type.
   *
   * @param source The source configuration
   * @param type The stream type (LIVE, VOD, SERIES)
   * @return Multi stream of stream data maps
   */
  private Multi<Map> fetchStreams(Source source, StreamType type) {
    String action = type.getStreamAction();
    String url = buildApiUrl(source, action);

    LOGGER.info(
        String.format("Fetching %s for source: %s", type.getStreamTypeName(), source.getName()));

    return httpStreamingService
        .streamJson(url, Map.class, createDefaultHttpOptions())
        .onFailure()
        .invoke(
            ex ->
                LOGGER.severe(
                    String.format(
                        "Failed to fetch %s from source %s: %s",
                        type.getStreamTypeName(), source.getName(), ex.getMessage())));
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
   * Create default HTTP options for Xtream API calls.
   *
   * @return Configured HttpOptions with sensible defaults
   */
  private HttpOptions createDefaultHttpOptions() {
    return HttpOptions.builder()
        .timeout(DEFAULT_TIMEOUT_SECONDS)
        .maxRetries(DEFAULT_MAX_RETRIES)
        .build();
  }
}
