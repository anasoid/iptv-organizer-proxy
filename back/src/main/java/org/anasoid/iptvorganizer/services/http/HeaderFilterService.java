package org.anasoid.iptvorganizer.services.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized header filtering for HTTP proxy operations.
 *
 * <p>Implements bidirectional header filtering: 1. Client → Upstream: Forward relevant headers
 * (User-Agent, Range, etc) while skipping connection-related headers 2. Upstream → Client:
 * Propagate response headers (Content-Type, Content-Range, etc) while removing proxy-specific
 * headers
 *
 * <p>This matches the PHP implementation's header forwarding behavior for proper VOD seeking,
 * content negotiation, and client disconnection handling.
 */
@Slf4j
@ApplicationScoped
public class HeaderFilterService {

  // Headers to skip when forwarding client requests to upstream
  private static final Set<String> SKIP_REQUEST_HEADERS =
      Set.of(
          "host",
          "connection",
          "content-length",
          "transfer-encoding",
          "upgrade",
          "expect",
          "proxy-connection",
          "proxy-authenticate",
          "te",
          "authorization"); // Don't forward auth to upstream (uses source creds)

  // Headers to skip when forwarding upstream responses to client
  private static final Set<String> SKIP_RESPONSE_HEADERS =
      Set.of(
          "transfer-encoding",
          "proxy-connection",
          "connection",
          "keep-alive"); // NOTE: content-encoding is NOT skipped - needed for decompression

  /**
   * Extract and filter request headers from JAX-RS HttpHeaders.
   *
   * @param httpHeaders JAX-RS HttpHeaders from request
   * @return Filtered headers as Map<String, String> (single-valued) for HttpRequest
   */
  public Map<String, String> filterRequestHeaders(HttpHeaders httpHeaders) {
    if (httpHeaders == null) {
      return Collections.emptyMap();
    }

    Map<String, String> filteredHeaders = new HashMap<>();

    // Iterate through all headers in the request
    for (String headerName : httpHeaders.getRequestHeaders().keySet()) {
      String headerNameLower = headerName.toLowerCase();

      // Skip headers we don't want to forward
      if (SKIP_REQUEST_HEADERS.contains(headerNameLower)) {
        log.debug("Skipping request header: {}", headerName);
        continue;
      }

      // Get the first value of the header
      List<String> values = httpHeaders.getRequestHeaders().get(headerName);
      if (values != null && !values.isEmpty()) {
        String headerValue = values.get(0);
        filteredHeaders.put(headerName, headerValue);
        log.debug("Forwarding request header: {} = {}", headerName, headerValue);
      }
    }

    return filteredHeaders;
  }

  /**
   * Apply upstream response headers to the response builder.
   *
   * <p>Filters headers from upstream and adds them to the JAX-RS response builder.
   *
   * @param builder JAX-RS Response.ResponseBuilder to add headers to
   * @param upstreamHeaders Headers from upstream response (Map<String, List<String>> format from
   *     HttpClient)
   */
  public void applyResponseHeaders(
      Response.ResponseBuilder builder, Map<String, List<String>> upstreamHeaders) {
    if (upstreamHeaders == null || upstreamHeaders.isEmpty()) {
      return;
    }

    for (Map.Entry<String, List<String>> entry : upstreamHeaders.entrySet()) {
      String headerName = entry.getKey();
      String headerNameLower = headerName.toLowerCase();

      // Skip headers we don't want to forward
      if (SKIP_RESPONSE_HEADERS.contains(headerNameLower)) {
        log.debug("Skipping response header: {}", headerName);
        continue;
      }

      // Add all values of the header to the response
      List<String> values = entry.getValue();
      if (values != null && !values.isEmpty()) {
        for (String value : values) {
          builder.header(headerName, value);
          log.debug("Applied response header: {} = {}", headerName, value);
        }
      }
    }
  }

  /**
   * Check if a header should be forwarded in requests.
   *
   * @param headerName Header name (case-insensitive)
   * @return true if header should be forwarded
   */
  public boolean shouldForwardRequestHeader(String headerName) {
    return !SKIP_REQUEST_HEADERS.contains(headerName.toLowerCase());
  }

  /**
   * Check if a header should be forwarded in responses.
   *
   * @param headerName Header name (case-insensitive)
   * @return true if header should be forwarded
   */
  public boolean shouldForwardResponseHeader(String headerName) {
    return !SKIP_RESPONSE_HEADERS.contains(headerName.toLowerCase());
  }
}
