package org.anasoid.iptvorganizer.services.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.stream.*;

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
    int maxRetries = finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 3;

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
  public <T> Multi<T> streamJson(String url, Class<T> targetClass, HttpOptions options) {
    if (options == null) {
      options = new HttpOptions();
    }

    final String finalUrl = url;
    final HttpOptions finalOptions = options;

    LOGGER.info("Streaming JSON from URL: " + finalUrl);

    return Multi.createFrom()
        .deferred(
            () -> {
              try {
                // Create piped streams for streaming JSON parsing
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out);

                // Stream HTTP buffers directly without collecting to memory
                streamHttp(finalUrl, finalOptions)
                    .subscribe()
                    .with(
                        buffer -> {
                          try {
                            out.write(buffer.getBytes());
                            LOGGER.fine(
                                "Streamed buffer of "
                                    + buffer.length()
                                    + " bytes from: "
                                    + finalUrl);
                          } catch (Exception e) {
                            LOGGER.severe(
                                "Failed to write buffer to stream from: "
                                    + finalUrl
                                    + ", error: "
                                    + e.getMessage());
                            throw new StreamingException("Failed to write buffer to stream", e);
                          }
                        },
                        failure -> {
                          LOGGER.severe(
                              "HTTP streaming failed from: "
                                  + finalUrl
                                  + ", error: "
                                  + failure.getMessage());
                          try {
                            out.close();
                          } catch (Exception e) {
                            // Ignore close errors
                          }
                        },
                        () -> {
                          try {
                            out.close();
                            LOGGER.info("Completed streaming from: " + finalUrl);
                          } catch (Exception e) {
                            LOGGER.warning(
                                "Error closing stream for: "
                                    + finalUrl
                                    + ", error: "
                                    + e.getMessage());
                          }
                        });

                // Offload blocking JSON parsing to worker thread to avoid blocking event loop
                // PipedInputStream.read() is blocking and must not run on event loop thread
                return Uni.createFrom()
                    .item(in)
                    .emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor())
                    .onItem()
                    .transformToMulti(
                        inputStream -> jsonParser.parseJsonArray(inputStream, targetClass));
              } catch (Exception e) {
                LOGGER.severe(
                    "Failed to parse JSON stream from: " + finalUrl + ", error: " + e.getMessage());
                return Multi.createFrom()
                    .failure(new StreamingException("Failed to parse JSON stream", e));
              }
            });
  }

  /** Configure HTTP request with options */
  private HttpRequest<Buffer> configureRequest(String url, HttpOptions options) {
    HttpRequest<Buffer> request = webClient.getAbs(url);

    // Add authentication if provided
    if (options.getUsername() != null && options.getPassword() != null) {
      applyBasicAuth(request, options.getUsername(), options.getPassword());
    }

    // Add custom headers
    if (options.getHeaders() != null) {
      options.getHeaders().forEach((key, value) -> request.putHeader(key, value));
    }

    // Set timeout
    if (options.getTimeout() != null) {
      request.timeout(options.getTimeout() * 1000L);
    }

    return request;
  }

  /** Apply HTTP Basic Authentication */
  private void applyBasicAuth(HttpRequest<Buffer> request, String username, String password) {
    String credentials = username + ":" + password;
    String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
    request.putHeader("Authorization", "Basic " + encodedCredentials);
  }

  /** Stream HTTP response as Multi<Buffer> with backpressure (true streaming without buffering) */
  private Multi<Buffer> streamResponse(HttpRequest<Buffer> request, String url) {
    return Multi.createFrom()
        .emitter(
            emitter -> {
              try {
                URI uri = new URI(url);
                HttpClient httpClient = vertx.createHttpClient();

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

                // Add authentication if provided (collect headers from configured request)
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                if (request.headers() != null) {
                  request
                      .headers()
                      .forEach(header -> headers.put(header.getKey(), header.getValue()));
                }

                // Create streaming request asynchronously - response chunks are emitted without
                // buffering
                httpClient
                    .request(HttpMethod.GET, port, host, path)
                    .onSuccess(
                        clientRequest -> {
                          // Set timeout
                          clientRequest.setTimeout(30000L);

                          // Add headers
                          headers.forEach(clientRequest::putHeader);

                          // Set response handler before sending
                          clientRequest.response(
                              asyncResult -> {
                                if (asyncResult.failed()) {
                                  LOGGER.severe(
                                      "HTTP request failed for: "
                                          + url
                                          + ", error: "
                                          + asyncResult.cause().getMessage());
                                  httpClient.close();
                                  emitter.fail(
                                      new StreamingException(
                                          "HTTP request failed", asyncResult.cause()));
                                  return;
                                }

                                var response = asyncResult.result();
                                if (response.statusCode() >= 400) {
                                  LOGGER.severe(
                                      "HTTP error for: "
                                          + url
                                          + ", status: "
                                          + response.statusCode()
                                          + " "
                                          + response.statusMessage());
                                  httpClient.close();
                                  emitter.fail(
                                      new StreamingException(
                                          "HTTP error: "
                                              + response.statusCode()
                                              + " "
                                              + response.statusMessage()));
                                  return;
                                }

                                LOGGER.info(
                                    "HTTP response started for: "
                                        + url
                                        + ", status: "
                                        + response.statusCode());

                                // Emit each chunk as it arrives (true streaming - no buffering
                                // entire body)
                                response.handler(
                                    buffer -> {
                                      try {
                                        LOGGER.fine(
                                            "Received chunk of "
                                                + buffer.length()
                                                + " bytes from: "
                                                + url);
                                        emitter.emit(buffer);
                                      } catch (Exception e) {
                                        LOGGER.severe(
                                            "Failed to emit buffer from: "
                                                + url
                                                + ", error: "
                                                + e.getMessage());
                                        emitter.fail(
                                            new StreamingException("Failed to emit buffer", e));
                                      }
                                    });

                                // Complete when response ends
                                response.endHandler(
                                    v -> {
                                      LOGGER.info("Completed streaming from: " + url);
                                      httpClient.close();
                                      emitter.complete();
                                    });

                                // Handle response errors
                                response.exceptionHandler(
                                    failure -> {
                                      LOGGER.severe(
                                          "HTTP response error from: "
                                              + url
                                              + ", error: "
                                              + failure.getMessage());
                                      httpClient.close();
                                      emitter.fail(
                                          new StreamingException("HTTP response error", failure));
                                    });
                              });

                          // Send request
                          clientRequest.end();
                        })
                    .onFailure(
                        failure -> {
                          LOGGER.severe(
                              "Failed to create HTTP request for: "
                                  + url
                                  + ", error: "
                                  + failure.getMessage());
                          httpClient.close();
                          emitter.fail(
                              new StreamingException("Failed to create HTTP request", failure));
                        });

              } catch (Exception e) {
                LOGGER.severe(
                    "Failed to create HTTP request for: " + url + ", error: " + e.getMessage());
                emitter.fail(new StreamingException("Failed to create HTTP request", e));
              }
            });
  }
}
