package org.anasoid.iptvorganizer.utils.streaming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.http.HeaderFilterService;

/**
 * Stream Proxy HTTP Client
 *
 * <p>Dedicated HTTP client for stream proxying with redirect following and header management.
 *
 * <p>Handles the following responsibilities: - Building HTTP options based on client/source
 * configuration (with fallback hierarchy) - Filtering and forwarding request headers - Making HTTP
 * requests with redirect following support - Returning streaming response with status and headers
 *
 * <p>This class centralizes stream proxy HTTP logic, making it reusable across controllers.
 */
@Slf4j
@ApplicationScoped
public class StreamProxyHttpClient {

  @Inject HttpStreamingService httpStreamingService;
  @Inject HeaderFilterService headerFilterService;
  @Inject ClientService clientService;

  /**
   * Load stream from upstream URL via proxy with header forwarding and redirect following
   *
   * <p>Handles: - Filtering request headers to forward to upstream - Building HTTP options based on
   * client/source configuration - Making HTTP request with redirect following if configured -
   * Checking for HTTP errors
   *
   * @param upstreamUrl The upstream URL to fetch from
   * @param client The authenticated client (for configuration fallback)
   * @param source The source (for configuration)
   * @param httpHeaders Client request headers to filter and forward
   * @return HttpStreamingResponse with status, headers, and body
   * @throws Exception if streaming fails
   */
  public HttpStreamingResponse loadStreamWithProxy(
      String upstreamUrl, Client client, Source source, HttpHeaders httpHeaders) {
    log.info("Loading stream via proxy: {}", upstreamUrl);

    // Extract and filter client headers to forward
    Map<String, String> requestHeaders = headerFilterService.filterRequestHeaders(httpHeaders);

    // Build HTTP options with redirect following based on client/source config
    HttpOptions options = buildHttpOptions(client, source);

    // Stream content from upstream with header forwarding
    HttpStreamingResponse streamResponse =
        httpStreamingService.streamHttpWithHeaders(upstreamUrl, options, requestHeaders);

    // Check for HTTP errors
    if (streamResponse.getStatusCode() >= 400) {
      log.warn(
          "Upstream error response: {} for URL: {}", streamResponse.getStatusCode(), upstreamUrl);
    }

    return streamResponse;
  }

  /**
   * Build HTTP options from client/source configuration
   *
   * <p>Configuration priority: client -> source -> defaults
   *
   * @param client The client
   * @param source The source
   * @return Configured HttpOptions
   */
  private HttpOptions buildHttpOptions(Client client, Source source) {
    // Resolve followRedirects setting: client -> source -> default false
    boolean followRedirects = clientService.resolveStreamFollowLocation(client, source);

    log.debug("HTTP proxy options - followRedirects: {}", followRedirects);

    return HttpOptions.builder()
        .timeout(30000L)
        .maxRetries(1)
        .followRedirects(followRedirects)
        .build();
  }
}
