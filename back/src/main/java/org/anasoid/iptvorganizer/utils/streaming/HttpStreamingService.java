package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ProxyException;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.utils.proxy.HttpClientFactory;

@Slf4j
@ApplicationScoped
public class HttpStreamingService {

  @Inject StreamingJsonParser jsonParser;

  @Inject ObjectMapper objectMapper;

  @Inject HttpClientFactory httpClientFactory;

  /** Stream HTTP response as InputStream with optional proxy support */
  public InputStream streamHttp(String url, HttpOptions options, Source source) {
    if (options == null) {
      options = new HttpOptions();
    }

    final HttpOptions finalOptions = options;
    int maxRetries = finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 1;

    log.info("Starting HTTP stream request to: {}", url);

    // Sequential retry logic
    Exception lastException = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return performHttpRequest(url, finalOptions, source);
      } catch (Exception e) {
        lastException = e;
        if (attempt < maxRetries) {
          log.info("HTTP request failed (attempt {}), retrying...", (attempt + 1));
        }
      }
    }

    log.error("HTTP streaming failed after {} retries for: {}", maxRetries, url);
    throw new StreamingException(
        "HTTP streaming failed after " + maxRetries + " retries", lastException);
  }

  /** Perform actual HTTP request and return response body as InputStream */
  private InputStream performHttpRequest(String url, HttpOptions options) {
    return performHttpRequest(url, options, null);
  }

  /** Perform actual HTTP request with optional proxy support */
  private InputStream performHttpRequest(String url, HttpOptions options, Source source) {
    try {
      // Determine redirect policy
      boolean followRedirects =
          options.getFollowRedirects() == null || options.getFollowRedirects();

      // Get HttpClient with proxy configuration if proxy is configured in options
      HttpClient clientToUse;
      if (options.getProxy() != null) {
        clientToUse = httpClientFactory.createClientWithProxy(options.getProxy(), followRedirects);
      } else {
        // Backward compatibility: use default client behavior
        clientToUse =
            followRedirects
                ? HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                : HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
      }

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(new URI(url)).GET();

      // Add custom headers
      if (options.getHeaders() != null) {
        options.getHeaders().forEach(requestBuilder::header);
      }

      // Set timeout
      if (options.getTimeout() != null) {
        requestBuilder.timeout(Duration.ofMillis(options.getTimeout()));
      }

      HttpRequest request = requestBuilder.build();

      HttpResponse<InputStream> response =
          clientToUse.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() >= 400) {
        throw new StreamingException(
            "HTTP error: " + response.statusCode() + " " + response.statusCode());
      }

      log.info("HTTP request successful for: {}, status: {}", url, response.statusCode());
      return response.body();
    } catch (ProxyException e) {
      log.error("Proxy error during HTTP request for: {}, error: {}", url, e.getMessage());
      throw e;
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error("HTTP request failed for: {}, error: {}", url, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  /**
   * Stream HTTP response with request header forwarding and response header capture.
   *
   * <p>This method enhances the basic streamHttp() by: - Forwarding client headers (User-Agent,
   * Range, etc) to upstream - Capturing and returning response headers for propagation to client
   *
   * @param url The URL to stream from
   * @param options HTTP options (timeout, retries, headers)
   * @param requestHeaders Headers to forward to upstream (client headers)
   * @return HttpStreamingResponse with status, headers, and body
   */
  public HttpStreamingResponse streamHttpWithHeaders(
      String url, HttpOptions options, Map<String, String> requestHeaders) {
    return streamHttpWithHeaders(url, options, requestHeaders, null);
  }

  /**
   * Stream HTTP response with request header forwarding and response header capture, with optional
   * proxy support.
   *
   * @param url The URL to stream from
   * @param options HTTP options (timeout, retries, headers)
   * @param requestHeaders Headers to forward to upstream (client headers)
   * @param source The source for proxy configuration (optional)
   * @return HttpStreamingResponse with status, headers, and body
   */
  public HttpStreamingResponse streamHttpWithHeaders(
      String url, HttpOptions options, Map<String, String> requestHeaders, Source source) {
    if (options == null) {
      options = createHttpOptions();
    }

    final HttpOptions finalOptions = options;
    int maxRetries = finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 1;

    log.debug("Starting HTTP stream request with headers to: {}", url);

    // Sequential retry logic
    Exception lastException = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return performHttpRequestWithHeaders(url, finalOptions, requestHeaders, source);
      } catch (Exception e) {
        lastException = e;
        if (attempt < maxRetries) {
          log.info("HTTP request with headers failed (attempt {}), retrying...", (attempt + 1));
        }
      }
    }

    log.error("HTTP streaming with headers failed after {} retries for: {}", maxRetries, url);
    throw new StreamingException(
        "HTTP streaming with headers failed after " + maxRetries + " retries", lastException);
  }

  /**
   * Perform actual HTTP request with request headers and return response with headers.
   *
   * @param url The URL to request
   * @param options HTTP options
   * @param requestHeaders Headers to forward
   * @return HttpStreamingResponse with status, headers, and body
   */
  private HttpStreamingResponse performHttpRequestWithHeaders(
      String url, HttpOptions options, Map<String, String> requestHeaders) {
    return performHttpRequestWithHeaders(url, options, requestHeaders, null);
  }

  /**
   * Perform actual HTTP request with request headers and optional proxy support.
   *
   * @param url The URL to request
   * @param options HTTP options
   * @param requestHeaders Headers to forward
   * @param source The source for proxy configuration (optional)
   * @return HttpStreamingResponse with status, headers, and body
   */
  private HttpStreamingResponse performHttpRequestWithHeaders(
      String url, HttpOptions options, Map<String, String> requestHeaders, Source source) {
    try {
      // Determine redirect policy
      boolean followRedirects =
          options.getFollowRedirects() == null || options.getFollowRedirects();

      // Get HttpClient with proxy configuration if proxy is configured in options
      HttpClient clientToUse;
      if (options.getProxy() != null) {
        clientToUse = httpClientFactory.createClientWithProxy(options.getProxy(), followRedirects);
      } else {
        // Backward compatibility: use default client behavior
        clientToUse =
            followRedirects
                ? HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                : HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
      }

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(new URI(url)).GET();

      // Add options headers
      if (options.getHeaders() != null) {
        options.getHeaders().forEach(requestBuilder::header);
      }

      // Add forwarded request headers
      if (requestHeaders != null) {
        requestHeaders.forEach(
            (k, v) -> {
              requestBuilder.header(k, v);
              log.debug("Forwarded request header: {} = {}", k, v);
            });
      }

      // Set timeout
      if (options.getTimeout() != null) {
        requestBuilder.timeout(Duration.ofMillis(options.getTimeout()));
      }

      HttpRequest request = requestBuilder.build();

      HttpResponse<InputStream> response =
          clientToUse.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() >= 400) {
        log.warn("HTTP error response: {} for URL: {}", response.statusCode(), url);
      }

      log.debug(
          "HTTP request with headers successful for: {}, status: {}", url, response.statusCode());

      return HttpStreamingResponse.builder()
          .statusCode(response.statusCode())
          .headers(response.headers().map())
          .body(response.body())
          .build();
    } catch (ProxyException e) {
      log.error(
          "Proxy error during HTTP request with headers for: {}, error: {}", url, e.getMessage());
      throw e;
    } catch (IOException | InterruptedException | URISyntaxException e) {
      log.error("HTTP request with headers failed for: {}, error: {}", url, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private HttpOptions createHttpOptions() {
    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30000L);
    httpOptions.setMaxRetries(1);
    return httpOptions;
  }

  /**
   * Stream HTTP response as JSON objects with automatic parsing and optional proxy support.
   *
   * @param url The URL to stream from
   * @param options HTTP options (timeout, retries, headers)
   * @param source The source for proxy configuration (optional)
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> streamJsonArray(
      String url, HttpOptions options, Source source) {
    if (options == null) {
      options = createHttpOptions();
    }

    log.info("Streaming JSON from URL: {}", url);

    try {
      // Fetch HTTP response as InputStream
      InputStream is = streamHttp(url, options, source);

      // Parse JSON array from InputStream using Iterator-based streaming
      @SuppressWarnings("unchecked")
      JsonStreamResult<Map<?, ?>> result =
          (JsonStreamResult<Map<?, ?>>)
              (JsonStreamResult<?>) jsonParser.parseJsonArray(is, Map.class);

      log.info("Successfully starting JSON stream from: {}", url);
      return result;
    } catch (Exception e) {
      log.error("Failed to parse JSON stream from: {}, error: {}", url, e.getMessage());
      throw new StreamingException("Failed to parse JSON stream", e);
    }
  }

  /**
   * Fetch single JSON object from HTTP endpoint with optional proxy support.
   *
   * @param url The URL to fetch from
   * @param options HTTP options (timeout, retries, headers)
   * @param source The source for proxy configuration (optional)
   * @return Map containing the JSON object
   * @throws Exception if fetch or parsing fails
   */
  public Map<String, Object> fetchJsonObject(String url, HttpOptions options, Source source) {
    if (options == null) {
      options = createHttpOptions();
    }

    log.info("Fetching JSON object from URL: {}", url);

    InputStream is = performHttpRequest(url, options, source);
    try {
      Map<String, Object> result = objectMapper.readValue(is, new TypeReference<>() {});
      log.info("Successfully fetched JSON object from: {}", url);
      return result;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
