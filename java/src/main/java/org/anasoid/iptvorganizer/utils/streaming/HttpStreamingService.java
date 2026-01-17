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
import java.util.List;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.http.HttpOptions;

@ApplicationScoped
public class HttpStreamingService {

  private static final Logger LOGGER = Logger.getLogger(HttpStreamingService.class.getName());

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

    LOGGER.info("Starting HTTP stream request to: " + url);

    // Sequential retry logic
    Exception lastException = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return performHttpRequest(url, finalOptions);
      } catch (Exception e) {
        lastException = e;
        if (attempt < maxRetries) {
          LOGGER.info("HTTP request failed (attempt " + (attempt + 1) + "), retrying...");
        }
      }
    }

    LOGGER.severe("HTTP streaming failed after " + maxRetries + " retries for: " + url);
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

      LOGGER.info("HTTP request successful for: " + url + ", status: " + response.statusCode());
      return response.body();
    } catch (Exception e) {
      LOGGER.severe("HTTP request failed for: " + url + ", error: " + e.getMessage());
      throw e;
    }
  }

  /**
   * Stream HTTP response as JSON objects with automatic parsing (memory efficient true streaming)
   */
  public <T> List<T> streamJson(String url, Class<T> targetClass) {
    return streamJson(url, targetClass, null);
  }

  private HttpOptions createHttpOptions() {
    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30000L);
    httpOptions.setMaxRetries(1);
    return httpOptions;
  }

  public <T> List<T> streamJson(String url, Class<T> targetClass, HttpOptions options) {
    if (options == null) {
      options = createHttpOptions();
    }

    LOGGER.info("Streaming JSON from URL: " + url);

    try {
      // Fetch HTTP response as InputStream
      InputStream is = streamHttp(url, options);

      // Parse JSON array from InputStream
      List<T> result = jsonParser.parseJsonArray(is, targetClass);

      LOGGER.info("Successfully parsed JSON from: " + url);
      return result;
    } catch (Exception e) {
      LOGGER.severe("Failed to parse JSON stream from: " + url + ", error: " + e.getMessage());
      throw new StreamingException("Failed to parse JSON stream", e);
    }
  }
}
