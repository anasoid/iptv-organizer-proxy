package org.anasoid.iptvorganizer.controllers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.http.HeaderFilterService;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;

/**
 * Proxy Controller
 *
 * <p>Handles stream proxying with base64 URL decoding and optional redirect following
 *
 * <p>Endpoint: GET /proxy/{username}/{password}?url={base64_encoded_url}
 *
 * <p>This controller is used when `disablestreamproxy=false`. The StreamDataController encodes the
 * upstream URL in base64 and redirects to this endpoint, which then streams the content from the
 * upstream source.
 */
@Slf4j
@Path("/proxy")
@ApplicationScoped
public class ProxyController {

  private static final int BUFFER_SIZE = 8192;

  @Inject XtreamUserService xtreamUserService;
  @Inject HttpStreamingService httpStreamingService;
  @Inject HeaderFilterService headerFilterService;
  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;

  /**
   * Handle proxy stream request
   *
   * <p>Endpoint: GET /proxy/{username}/{password}?url={base64_encoded_url}
   *
   * @param username Client username
   * @param password Client password
   * @param encodedUrl Base64 encoded upstream URL
   * @param httpHeaders Client request headers
   * @return Stream response with content from upstream
   */
  @GET
  @Path("/{username}/{password}")
  public Response handleProxyRequest(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @QueryParam("url") String encodedUrl,
      @jakarta.ws.rs.core.Context HttpHeaders httpHeaders) {

    try {
      // Validate encoded URL parameter
      if (encodedUrl == null || encodedUrl.isEmpty()) {
        log.warn("Proxy request with missing URL");
        return Response.status(Response.Status.BAD_REQUEST).build();
      }

      // Validate and authenticate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      // Decode URL
      String decodedUrl = decodeUrl(encodedUrl);
      log.info("Proxy request - user: {}, decodedUrl: {}", username, decodedUrl);

      // Stream content from upstream
      return streamFromUpstream(decodedUrl, source, httpHeaders);

    } catch (UnauthorizedException ex) {
      log.warn("Proxy request unauthorized: {}", ex.getMessage());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (ForbiddenException ex) {
      log.warn("Proxy request forbidden: {}", ex.getMessage());
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (IllegalArgumentException ex) {
      log.warn("Invalid proxy URL encoding: {}", ex.getMessage());
      return Response.status(Response.Status.BAD_REQUEST).build();
    } catch (Exception ex) {
      log.error("Error handling proxy request: {}", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Decode base64 URL
   *
   * @param encodedUrl Base64 encoded URL
   * @return Decoded URL
   * @throws IllegalArgumentException if decoding fails
   */
  private String decodeUrl(String encodedUrl) {
    try {
      // URL decode first (in case it's URL encoded)
      String urlDecoded = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

      // Then base64 decode
      byte[] decodedBytes = Base64.getUrlDecoder().decode(urlDecoded);
      return new String(decodedBytes, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid base64 encoding in URL parameter", ex);
    }
  }

  /**
   * Stream content from upstream URL with header forwarding
   *
   * @param upstreamUrl The upstream URL
   * @param source The source (for configuration)
   * @param httpHeaders Client request headers to forward
   * @return Response with streaming output
   */
  private Response streamFromUpstream(String upstreamUrl, Source source, HttpHeaders httpHeaders) {
    try {
      // Extract and filter client headers to forward
      Map<String, String> requestHeaders = headerFilterService.filterRequestHeaders(httpHeaders);

      // Build HTTP options with redirect following if configured
      HttpOptions options =
          HttpOptions.builder()
              .timeout(30000L)
              .maxRetries(1)
              .followRedirects(
                  source.getStreamFollowLocation() != null && source.getStreamFollowLocation())
              .build();

      // Stream content from upstream with header forwarding
      HttpStreamingResponse streamResponse =
          httpStreamingService.streamHttpWithHeaders(upstreamUrl, options, requestHeaders);

      // Check for HTTP errors
      if (streamResponse.getStatusCode() >= 400) {
        log.warn(
            "Upstream error response: {} for URL: {}", streamResponse.getStatusCode(), upstreamUrl);
        return Response.status(streamResponse.getStatusCode()).build();
      }

      // Build response with streaming output
      Response.ResponseBuilder responseBuilder =
          Response.ok(
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
                      } else {
                        log.warn(
                            "Error streaming content from {}: {}", upstreamUrl, ex.getMessage());
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

      // Apply upstream response headers
      headerFilterService.applyResponseHeaders(responseBuilder, streamResponse.getHeaders());

      return responseBuilder.build();

    } catch (Exception ex) {
      log.error("Error getting stream from upstream: {}", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }
}
