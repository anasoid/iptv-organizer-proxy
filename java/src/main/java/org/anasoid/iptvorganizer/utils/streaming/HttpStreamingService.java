package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedInputStream;
import java.net.URI;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.http.HttpOptions;

@ApplicationScoped
public class HttpStreamingService {

  private static final Logger LOGGER = Logger.getLogger(HttpStreamingService.class.getName());

  @Inject WebClient webClient;

  @Inject StreamingJsonParser jsonParser;

  @Inject ObjectMapper objectMapper;

  @Inject Vertx vertx;

  /** Stream HTTP response as buffers with backpressure support */
  public Multi<Buffer> streamHttp(String url, HttpOptions options) {
    if (options == null) {
      options = new HttpOptions();
    }

    final HttpOptions finalOptions = options;
    int maxRetries = finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 1;

    LOGGER.info("Starting HTTP stream request to: " + url);

    return Multi.createFrom()
        .deferred(
            () -> {
              HttpRequest<Buffer> request = configureRequest(url, finalOptions);
              return streamResponse(request, url);
            })
        .onFailure()
        .retry()
        .atMost(maxRetries)
        .onFailure()
        .transform(
            failure -> {
              LOGGER.severe(
                  "HTTP streaming failed after "
                      + maxRetries
                      + " retries for: "
                      + url
                      + ", error: "
                      + failure.getMessage());
              return new StreamingException(
                  "HTTP streaming failed after " + maxRetries + " retries", failure);
            });
  }

  /**
   * Stream HTTP response as JSON objects with automatic parsing (memory efficient true streaming)
   * Offloads blocking JSON parsing to worker thread to avoid blocking event loop
   */
  public <T> Multi<T> streamJson(String url, Class<T> targetClass) {
    return streamJson(url, targetClass, null);
  }

  private HttpOptions createHttpOptions() {
    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30000L);
    httpOptions.setMaxRetries(1);
    return httpOptions;
  }

  public <T> Multi<T> streamJson(String url, Class<T> targetClass, HttpOptions options) {
    if (options == null) {
      options = createHttpOptions();
    }

    final String finalUrl = url;
    final HttpOptions finalOptions = options;

    LOGGER.info("Streaming JSON from URL: " + finalUrl);

    return Multi.createFrom()
        .deferred(
            () -> {
              try {
                // Create reactive input stream with proper backpressure support
                // Memory-efficient: Only ONE buffer (8-32KB) in memory at a time
                ReactiveInputStream reactiveInputStream =
                    new ReactiveInputStream(streamHttp(finalUrl, finalOptions));

                // Wrap with BufferedInputStream to improve performance
                // 64KB buffer significantly reduces read() system call overhead
                // Jackson parser will read from buffer instead of calling ReactiveInputStream each
                // time
                BufferedInputStream bufferedInputStream =
                    new BufferedInputStream(reactiveInputStream, 64000);

                // Return Multi directly - runSubscriptionOn() ensures deferred block runs on worker
                // thread
                // Backpressure flow: JSON parsing speed -> BufferedInputStream ->
                // ReactiveInputStream.read() ->
                // Multi<Buffer> demand -> HTTP fetch speed
                return jsonParser.parseJsonArray(bufferedInputStream, targetClass);
              } catch (Exception e) {
                LOGGER.severe(
                    "Failed to parse JSON stream from: " + finalUrl + ", error: " + e.getMessage());
                return Multi.createFrom()
                    .failure(new StreamingException("Failed to parse JSON stream", e));
              }
            })
        // CRITICAL: runSubscriptionOn() ensures the deferred() lambda AND all Multi operations
        // (including Jackson parser initialization and iterator.next() which calls blocking
        // ReactiveInputStream.read()) run on worker threads, not event loop.
        // This prevents "Thread blocked" warnings while maintaining full backpressure.
        .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor());
  }

  /** Configure HTTP request with options */
  private HttpRequest<Buffer> configureRequest(String url, HttpOptions options) {
    HttpRequest<Buffer> request = webClient.getAbs(url);

    // Add custom headers
    if (options.getHeaders() != null) {
      options.getHeaders().forEach(request::putHeader);
    }

    // Set timeout
    if (options.getTimeout() != null) {
      request.timeout(options.getTimeout());
    }

    return request;
  }

  /**
   * Stream HTTP response as Multi<Buffer> with TRUE backpressure using Mutiny-Vert.x integration.
   *
   * <p>This uses io.vertx.mutiny.core.http.HttpClient which provides native reactive streams
   * support. The response.toMulti() method properly handles backpressure by pausing HTTP reading
   * when downstream is slow, preventing unbounded buffer accumulation.
   *
   * <p>Memory: O(1) - only buffers actively being consumed, no queue accumulation.
   */
  private Multi<Buffer> streamResponse(HttpRequest<Buffer> request, String url) {
    return Uni.createFrom()
        .deferred(
            () -> {
              try {
                URI uri = new URI(url);

                // Use Mutiny-ified HttpClient for proper reactive streams integration
                io.vertx.mutiny.core.http.HttpClient httpClient =
                    vertx.createHttpClient(
                        new io.vertx.core.http.HttpClientOptions().setConnectTimeout(30000));

                int port = uri.getPort();
                if (port == -1) {
                  port = "https".equals(uri.getScheme()) ? 443 : 80;
                }

                String host = uri.getHost();
                String path = uri.getPath();
                if (uri.getQuery() != null) {
                  path += "?" + uri.getQuery();
                }
                if (path == null || path.isEmpty()) {
                  path = "/";
                }

                LOGGER.info("Starting HTTP request to: " + url);

                // Create request and send
                return httpClient
                    .request(HttpMethod.GET, port, host, path)
                    .onItem()
                    .transformToUni(
                        clientRequest -> {
                          // Set timeout
                          clientRequest.setTimeout(request.timeout());

                          // Add headers from configured request
                          if (request.headers() != null) {
                            request
                                .headers()
                                .forEach(
                                    header ->
                                        clientRequest.putHeader(
                                            header.getKey(), header.getValue()));
                          }

                          // Send request and get response
                          return clientRequest
                              .send()
                              .onItem()
                              .invoke(
                                  response -> {
                                    LOGGER.info(
                                        "HTTP response started for: "
                                            + url
                                            + ", status: "
                                            + response.statusCode());
                                  })
                              .onFailure()
                              .transform(
                                  failure -> {
                                    LOGGER.severe(
                                        "HTTP request failed for: "
                                            + url
                                            + ", error: "
                                            + failure.getMessage());
                                    httpClient.close();
                                    return new StreamingException("HTTP request failed", failure);
                                  });
                        })
                    .onItem()
                    .transformToUni(
                        response -> {
                          if (response.statusCode() >= 400) {
                            LOGGER.severe(
                                "HTTP error for: "
                                    + url
                                    + ", status: "
                                    + response.statusCode()
                                    + " "
                                    + response.statusMessage());
                            httpClient.close();
                            return Uni.createFrom()
                                .failure(
                                    new StreamingException(
                                        "HTTP error: "
                                            + response.statusCode()
                                            + " "
                                            + response.statusMessage()));
                          }

                          // Return success with response for downstream processing
                          return Uni.createFrom().item(response);
                        })
                    .onFailure()
                    .transform(
                        failure -> {
                          LOGGER.severe(
                              "Failed to create HTTP request for: "
                                  + url
                                  + ", error: "
                                  + failure.getMessage());
                          httpClient.close();
                          return new StreamingException("Failed to create HTTP request", failure);
                        });

              } catch (Exception e) {
                LOGGER.severe(
                    "Failed to create HTTP request for: " + url + ", error: " + e.getMessage());
                return Uni.createFrom()
                    .failure(new StreamingException("Failed to create HTTP request", e));
              }
            })
        .onItem()
        .transformToMulti(
            response -> {
              // CRITICAL: Use toMulti() from Mutiny-ified response
              // This provides TRUE backpressure - HTTP reading pauses when downstream is slow
              // No unbounded queue accumulation!
              return response
                  .toMulti()
                  .onItem()
                  .transform(
                      mutinyBuffer -> {
                        // Convert Mutiny buffer to core buffer
                        // Both wrap the same underlying data, no copy needed
                        LOGGER.fine("Received chunk of " + mutinyBuffer.length() + " bytes");
                        return mutinyBuffer.getDelegate();
                      })
                  .onCompletion()
                  .invoke(() -> LOGGER.info("Completed streaming from: " + url))
                  .onFailure()
                  .invoke(
                      failure ->
                          LOGGER.severe(
                              "HTTP response error from: "
                                  + url
                                  + ", error: "
                                  + failure.getMessage()));
            });
  }
}
