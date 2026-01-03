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

@ApplicationScoped
public class HttpStreamingService {

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

        return Multi.createFrom()
            .deferred(() -> {
                HttpRequest<Buffer> request = configureRequest(url, finalOptions);
                return streamResponse(request);
            })
            .onFailure()
            .retry()
            .atMost(finalOptions.getMaxRetries() != null ? finalOptions.getMaxRetries() : 3)
            .onFailure()
            .transform(failure -> new StreamingException("HTTP streaming failed after retries", failure));
    }

    /**
     * Stream HTTP response as JSON objects with automatic parsing
     */
    public <T> Multi<T> streamJson(String url, Class<T> targetClass, HttpOptions options) {
        if (options == null) {
            options = new HttpOptions();
        }

        return streamHttp(url, options)
            .collect()
            .asList()
            .onItem()
            .transformToMulti(buffers -> {
                try {
                    // Convert Mutiny buffers to input stream
                    PipedOutputStream out = new PipedOutputStream();
                    PipedInputStream in = new PipedInputStream(out);

                    // Write buffers to stream in background
                    Uni.createFrom().item(() -> {
                            try {
                                for (Buffer buffer : buffers) {
                                    out.write(buffer.getBytes());
                                }
                                out.close();
                            } catch (Exception e) {
                                throw new StreamingException("Failed to write buffers to stream", e);
                            }
                            return null;
                        })
                        .emitOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor())
                        .subscribe()
                        .with(item -> {}, failure -> {
                            try {
                                out.close();
                            } catch (Exception e) {
                                // Ignore close errors
                            }
                        });

                    // Parse JSON stream
                    return jsonParser.parseJsonArray(in, targetClass);
                } catch (Exception e) {
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
    private Multi<Buffer> streamResponse(HttpRequest<Buffer> request) {
        return Multi.createFrom()
            .emitter(emitter -> {
                request.send(asyncResult -> {
                    if (asyncResult.failed()) {
                        emitter.fail(new StreamingException("HTTP request failed", asyncResult.cause()));
                        return;
                    }

                    HttpResponse<Buffer> response = asyncResult.result();
                    if (response.statusCode() >= 400) {
                        emitter.fail(new StreamingException("HTTP error: " + response.statusCode() + " " + response.statusMessage()));
                        return;
                    }

                    // Emit the response body as a single buffer
                    emitter.emit(response.body());
                    emitter.complete();
                });
            });
    }
}
