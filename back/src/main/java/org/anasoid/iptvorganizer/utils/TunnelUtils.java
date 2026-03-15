package org.anasoid.iptvorganizer.utils;

import io.vertx.core.http.HttpClosedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.utils.streaming.StreamProxyHttpClient;

/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author : anasoid
 * Date :   3/15/26
 */
@Slf4j
@ApplicationScoped
public class TunnelUtils {

  /** Buffer size for streaming chunks. 64 KB balances memory and throughput for video streams. */
  private static final int BUFFER_SIZE = 65536;

  /** Hop-by-hop headers that must not be forwarded to the client. */
  private static final Set<String> SKIP_RESPONSE_HEADERS =
      Set.of("transfer-encoding", "connection", "keep-alive", "proxy-connection");

  @Inject StreamProxyHttpClient streamProxyHttpClient;

  /**
   * Stream content from upstream URL with header forwarding
   *
   * @param upstreamUrl The upstream URL
   * @param client The authenticated client (for configuration fallback)
   * @param source The source (for configuration)
   * @param httpHeaders Client request headers to forward
   * @return Response with streaming output
   */
  public Response streamFromUpstream(
      String upstreamUrl,
      Client client,
      Source source,
      HttpHeaders httpHeaders,
      HttpOptions options) {
    try {
      // Load stream from upstream via HTTP proxy client with forced redirect following
      HttpStreamingResponse streamResponse =
          streamProxyHttpClient.loadStreamWithProxy(
              upstreamUrl, client, source, httpHeaders, options);

      // Check for HTTP errors
      if (streamResponse.getStatusCode() >= 400) {
        return Response.status(streamResponse.getStatusCode()).build();
      }

      // Build response with streaming output, forwarding upstream status code
      Response.ResponseBuilder responseBuilder =
          Response.status(streamResponse.getStatusCode())
              .entity(
                  (StreamingOutput)
                      os -> {
                        InputStream inputStream = streamResponse.getBody();
                        try {
                          byte[] buffer = new byte[BUFFER_SIZE];
                          int bytesRead;
                          while ((bytesRead = inputStream.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                            os.flush();
                          }
                        } catch (IOException ex) {
                          // Handle client disconnection
                          if (ex.getMessage() != null
                              && (ex.getMessage().contains("Broken pipe")
                                  || ex.getMessage().contains("Connection reset"))) {
                            log.info("Client disconnected (normal for IPTV) - {}", upstreamUrl);
                          } else if (ex.getCause() instanceof HttpClosedException) {
                            // HTTP connection closed by client - normal for IPTV, do nothing
                          } else {
                            log.warn(
                                "Error streaming content from {}: {}",
                                upstreamUrl,
                                ex.getMessage());
                          }
                          throw ex;
                        } finally {
                          try {
                            inputStream.close();
                          } catch (IOException ex) {
                            log.warn("Error closing stream: {}", ex.getMessage());
                          }
                        }
                      });

      // Apply upstream response headers (Content-Type, Content-Length, Content-Range, etc.)
      for (Map.Entry<String, List<String>> header : streamResponse.getHeaders().entrySet()) {
        String headerName = header.getKey();
        // Skip hop-by-hop headers
        if (SKIP_RESPONSE_HEADERS.contains(headerName.toLowerCase())) {
          continue;
        }
        for (String headerValue : header.getValue()) {
          responseBuilder.header(headerName, headerValue);
        }
      }

      // Explicitly set Content-Type from upstream via type() to ensure it overrides
      // any JAX-RS default content type derived from the StreamingOutput entity
      String contentType = streamResponse.getHeader("content-type");
      if (contentType == null) {
        contentType = streamResponse.getHeader("Content-Type");
      }
      if (contentType != null && !contentType.isEmpty()) {
        responseBuilder.type(contentType);
      }

      return responseBuilder.build();

    } catch (Exception ex) {
      log.error("Error getting stream from upstream: {}", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Build HTTP options from client/source configuration
   *
   * <p>Configuration priority: forceFollowRedirects -> client -> source -> defaults
   *
   * @param client The client
   * @param source The source
   * @return Configured HttpOptions
   */
  public HttpOptions.HttpOptionsBuilder buildHttpOptions(Client client, Source source) {

    return streamProxyHttpClient.buildHttpOptions(client, source);
  }
}
