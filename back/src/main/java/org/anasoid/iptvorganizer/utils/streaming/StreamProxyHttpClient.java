package org.anasoid.iptvorganizer.utils.streaming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.models.http.RedirectCheckResult;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.ProxyConfigService;
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
  @Inject ProxyConfigService proxyConfigService;

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
    return loadStreamWithProxy(upstreamUrl, client, source, httpHeaders, false);
  }

  /**
   * Load stream from upstream URL via proxy with optional forced redirect following
   *
   * @param upstreamUrl The upstream URL to fetch from
   * @param client The authenticated client (for configuration fallback)
   * @param source The source (for configuration)
   * @param httpHeaders Client request headers to filter and forward
   * @param forceFollowRedirects Force following redirects regardless of config
   * @return HttpStreamingResponse with status, headers, and body
   * @throws Exception if streaming fails
   */
  public HttpStreamingResponse loadStreamWithProxy(
      String upstreamUrl,
      Client client,
      Source source,
      HttpHeaders httpHeaders,
      boolean forceFollowRedirects) {
    log.info("Loading stream via proxy: {}", upstreamUrl);

    // Extract and filter client headers to forward
    Map<String, String> requestHeaders = headerFilterService.filterRequestHeaders(httpHeaders);

    // Build HTTP options with redirect following and proxy config based on client/source config
    HttpOptions options = buildHttpOptions(client, source, forceFollowRedirects);

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
   * Check if upstream URL redirects (for credential hiding)
   *
   * <p>Makes a GET request with followRedirects=false to detect redirects without exposing
   * credentials to the client. Used when disableStreamProxy=true AND useRedirect=false to return
   * the redirect location instead of the URL containing source credentials.
   *
   * @param upstreamUrl The upstream URL to check (contains source credentials)
   * @param client The client (for configuration)
   * @param source The source (for configuration)
   * @param httpHeaders Client headers to forward
   * @return RedirectCheckResult with redirect status and location
   */
  public RedirectCheckResult checkForRedirect(
      String upstreamUrl, Client client, Source source, HttpHeaders httpHeaders) {

    log.info("Checking upstream for redirect to hide credentials: {}", upstreamUrl);

    // Extract and filter client headers
    Map<String, String> requestHeaders = headerFilterService.filterRequestHeaders(httpHeaders);

    // Build HTTP options with followRedirects=false
    HttpOptions options =
        HttpOptions.builder()
            .timeout(10000L) // Short timeout for redirect check
            .maxRetries(0) // No retries for redirect check
            .followRedirects(false) // CRITICAL: don't follow redirects
            .build();

    try {
      // Make GET request without following redirects
      HttpStreamingResponse response =
          httpStreamingService.streamHttpWithHeaders(upstreamUrl, options, requestHeaders);

      int statusCode = response.getStatusCode();

      // Check if it's a redirect status (301, 302, 307, 308)
      if (statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308) {
        // Extract Location header (case-insensitive)
        String location = extractLocationHeader(response.getHeaders());
        if (location != null && !location.isEmpty()) {
          log.info("Upstream redirects to: {}", location);
          return RedirectCheckResult.redirect(location);
        }
      }

      // Not a redirect
      log.warn("Upstream does not redirect, credentials would be exposed (status: {})", statusCode);
      return RedirectCheckResult.noRedirect(statusCode);

    } catch (Exception ex) {
      log.error("Error checking upstream redirect: {}", ex.getMessage(), ex);
      return RedirectCheckResult.error(ex.getMessage());
    }
  }

  /**
   * Extract Location header from response headers (case-insensitive)
   *
   * @param headers Response headers map
   * @return Location header value or null if not present
   */
  private String extractLocationHeader(Map<String, List<String>> headers) {
    if (headers == null) {
      return null;
    }

    // Try exact case match first
    List<String> locationValues = headers.get("Location");
    if (locationValues != null && !locationValues.isEmpty()) {
      return locationValues.get(0);
    }

    // Try lowercase match
    locationValues = headers.get("location");
    if (locationValues != null && !locationValues.isEmpty()) {
      return locationValues.get(0);
    }

    return null;
  }

  /**
   * Build HTTP options from client/source configuration
   *
   * <p>Configuration priority: forceFollowRedirects -> client -> source -> defaults
   *
   * @param client The client
   * @param source The source
   * @param forceFollowRedirects Force following redirects regardless of config
   * @return Configured HttpOptions
   */
  private HttpOptions buildHttpOptions(Client client, Source source, boolean forceFollowRedirects) {
    // Determine followRedirects: force true if forceFollowRedirects is set, else default to true
    // Note: streamFollowLocation has been removed in favor of enum-based connection modes
    boolean followRedirects = forceFollowRedirects || true; // Default to true for HTTP redirects

    // Get proxy configuration from source, respecting client enable flags
    Proxy proxy = proxyConfigService.getProxyConfig(client, source);

    log.debug(
        "HTTP proxy options - followRedirects: {}, proxy configured: {}",
        followRedirects,
        proxy != null);

    return HttpOptions.builder()
        .timeout(300000L)
        .maxRetries(1)
        .followRedirects(followRedirects)
        .proxy(proxy)
        .build();
  }
}
