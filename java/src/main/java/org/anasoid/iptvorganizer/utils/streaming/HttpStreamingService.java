package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.http.HttpOptions;

@Slf4j
@ApplicationScoped
public class HttpStreamingService {

  @Inject StreamingJsonParser jsonParser;

  @Inject ObjectMapper objectMapper;

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

  /** Stream HTTP response as InputStream */
  public InputStream streamHttp(String url, HttpOptions options) {
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
        return performHttpRequest(url, finalOptions);
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
  private InputStream performHttpRequest(String url, HttpOptions options) throws Exception {
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

    try {
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() >= 400) {
        throw new StreamingException(
            "HTTP error: " + response.statusCode() + " " + response.statusCode());
      }

      log.info("HTTP request successful for: {}, status: {}", url, response.statusCode());
      return response.body();
    } catch (Exception e) {
      log.error("HTTP request failed for: {}, error: {}", url, e.getMessage());
      throw e;
    }
  }

  private HttpOptions createHttpOptions() {
    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30000L);
    httpOptions.setMaxRetries(1);
    return httpOptions;
  }

  /**
   * Stream HTTP response as JSON objects with automatic parsing (memory efficient true streaming).
   *
   * @param url The URL to stream from
   * @param options HTTP options (timeout, retries, headers)
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  public JsonStreamResult<Map<?, ?>> streamJsonArray(String url, HttpOptions options) {
    if (options == null) {
      options = createHttpOptions();
    }

    log.info("Streaming JSON from URL: {}", url);

    try {
      // Fetch HTTP response as InputStream
      InputStream is = streamHttp(url, options);

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
}
