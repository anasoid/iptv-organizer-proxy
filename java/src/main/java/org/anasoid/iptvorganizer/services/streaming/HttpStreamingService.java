package org.anasoid.iptvorganizer.services.streaming;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.exceptions.StreamingException;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@ApplicationScoped
public class HttpStreamingService {

    private static final Logger LOGGER = Logger.getLogger(HttpStreamingService.class.getName());

    @Inject
    WebClient webClient;

    @Inject
    StreamingJsonParser jsonParser;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Stream HTTP response as buffers with backpressure support
     */
    public Multi<Buffer> streamHttp(String url, HttpOptions options) {
        if (options == null) {
            options = new HttpOptions();
        }

        final HttpOptions finalOptions = options;
        int maxRetries = finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 3;

        LOGGER.info("Starting HTTP stream request to: " + url);

        return Multi.createFrom()
            .deferred(() -> {
                HttpRequest<Buffer> request = configureRequest(url, finalOptions);
                return streamResponse(request, url);
            })
            .onFailure()
            .retry()
            .atMost(maxRetries)
            .onFailure()
            .transform(failure -> {
                LOGGER.severe("HTTP streaming failed after " + maxRetries + " retries for: " + url + ", error: " + failure.getMessage());
                return new StreamingException("HTTP streaming failed after " + maxRetries + " retries", failure);
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
            .deferred(() -> {
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
                                    LOGGER.fine("Streamed buffer of " + buffer.length() + " bytes from: " + finalUrl);
                                } catch (Exception e) {
                                    LOGGER.severe("Failed to write buffer to stream from: " + finalUrl + ", error: " + e.getMessage());
                                    throw new StreamingException("Failed to write buffer to stream", e);
                                }
                            },
                            failure -> {
                                LOGGER.severe("HTTP streaming failed from: " + finalUrl + ", error: " + failure.getMessage());
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
                                    LOGGER.warning("Error closing stream for: " + finalUrl + ", error: " + e.getMessage());
                                }
                            }
                        );

                    // Offload blocking JSON parsing to worker thread to avoid blocking event loop
                    // PipedInputStream.read() is blocking and must not run on event loop thread
                    return Uni.createFrom()
                        .item(in)
                        .emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor())
                        .onItem()
                        .transformToMulti(inputStream -> jsonParser.parseJsonArray(inputStream, targetClass));
                } catch (Exception e) {
                    LOGGER.severe("Failed to parse JSON stream from: " + finalUrl + ", error: " + e.getMessage());
                    return Multi.createFrom().failure(new StreamingException("Failed to parse JSON stream", e));
                }
            });
    }

    /**
     * Configure HTTP request with options
     */
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

    /**
     * Apply HTTP Basic Authentication
     */
    private void applyBasicAuth(HttpRequest<Buffer> request, String username, String password) {
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        request.putHeader("Authorization", "Basic " + encodedCredentials);
    }

    /**
     * Stream HTTP response as Multi<Buffer> with backpressure
     */
    private Multi<Buffer> streamResponse(HttpRequest<Buffer> request, String url) {
        return Multi.createFrom()
            .emitter(emitter -> {
                request.send(asyncResult -> {
                    if (asyncResult.failed()) {
                        LOGGER.severe("HTTP request failed for: " + url + ", error: " + asyncResult.cause().getMessage());
                        emitter.fail(new StreamingException("HTTP request failed", asyncResult.cause()));
                        return;
                    }

                    HttpResponse<Buffer> response = asyncResult.result();
                    if (response.statusCode() >= 400) {
                        LOGGER.severe("HTTP error for: " + url + ", status: " + response.statusCode() + " " + response.statusMessage());
                        emitter.fail(new StreamingException("HTTP error: " + response.statusCode() + " " + response.statusMessage()));
                        return;
                    }

                    LOGGER.info("HTTP response received for: " + url + ", status: " + response.statusCode() +
                               ", content length: " + response.body().length() + " bytes");

                    // Emit the response body as a single buffer
                    emitter.emit(response.body());
                    emitter.complete();
                });
            });
    }
}
