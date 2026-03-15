package org.anasoid.iptvorganizer.utils.streaming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.RedirectCheckResult;

@Slf4j
@ApplicationScoped
public class StreamModeHandler {

  private static final int CHUNK_SIZE = 8192;
  @Inject private StreamProxyHttpClient streamProxyHttpClient;
  @Inject private HttpStreamingService httpStreamingService;

  /**
   * Handle REDIRECT mode - send direct 302 redirect to upstream
   *
   * @param streamUrl The upstream stream URL
   * @return Response with redirect
   */
  public Response handleRedirectMode(String streamUrl) {
    return Response.seeOther(URI.create(streamUrl)).build();
  }

  /**
   * Handle DIRECT mode - check for upstream redirect to hide credentials
   *
   * @param streamUrl The upstream stream URL
   * @param client The authenticated client
   * @param source The source
   * @param httpHeaders Client request headers
   * @return Response with redirect or error
   */
  public Response handleDirectMode(
      String streamUrl, Client client, Source source, HttpHeaders httpHeaders) {
    RedirectCheckResult redirectCheck =
        streamProxyHttpClient.checkForRedirect(streamUrl, client, source, httpHeaders);

    if (redirectCheck.isError()) {
      return Response.status(Response.Status.BAD_GATEWAY)
          .entity("Upstream redirect check failed")
          .build();
    }
    if (redirectCheck.isRedirect()) {
      log.debug("Returning upstream redirect location: {}", redirectCheck.getLocation());
      return Response.seeOther(URI.create(redirectCheck.getLocation())).build();
    } else {
      log.warn(
          "Upstream does not redirect, cannot hide credentials. Status: {}",
          redirectCheck.getStatusCode());
      return Response.status(Response.Status.BAD_GATEWAY)
          .entity("Upstream does not provide credential-free redirect")
          .build();
    }
  }

  /**
   * Handle PROXY mode - encode URL and redirect to proxy endpoint
   *
   * @param uriInfo Request URI info
   * @param client Client
   * @param streamUrl The upstream stream URL
   * @return Response with redirect to proxy
   */
  public Response handleProxyMode(UriInfo uriInfo, Client client, String streamUrl) {

    String proxyUrl = buildProxyUrl(uriInfo, client, streamUrl);
    return Response.seeOther(URI.create(proxyUrl)).build();
  }

  /**
   * Handle TUNNEL mode - using application-level tunneling
   *
   * @param uriInfo Request URI info
   * @param client Client
   * @param streamUrl The upstream stream URL
   * @return Response with redirect to proxy (tunnel mode not yet implemented)
   */
  public Response handleTunnelMode(UriInfo uriInfo, Client client, String streamUrl) {
    // TODO: Implement tunnel mode
    // For now, fall back to proxy mode
    return handleProxyMode(uriInfo, client, streamUrl);
  }

  /**
   * Handle unknown stream mode
   *
   * @param streamMode The unknown stream mode
   * @return Response with error
   */
  public Response handleUnknownMode(String streamMode) {
    log.warn("Unknown stream mode: {}", streamMode);
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity("Unknown stream mode")
        .build();
  }

  /**
   * Stream XMLTV data from source
   *
   * @param client The client (for logging)
   * @return Response with streaming XMLTV data
   */
  public Response handleTunnelMode(Client client, String url, long timeout) {
    return Response.ok(
            (StreamingOutput)
                os -> {
                  InputStream inputStream = null;
                  try {
                    log.info("Streaming XMLTV for client: {}", client.getUsername());

                    // Fetch XMLTV data from upstream source
                    HttpOptions options =
                        HttpOptions.builder().timeout(timeout).maxRetries(1).build();
                    inputStream = httpStreamingService.streamHttp(url, options);
                    // Stream in chunks
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                      os.write(buffer, 0, bytesRead);
                      os.flush();
                    }
                    log.info("streaming completed for client: {}", client.getUsername());

                  } catch (IOException ex) {
                    // Handle client disconnection
                    if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
                      log.info("Client {} disconnected during streaming", client.getUsername());
                    } else {
                      log.warn(
                          "Error streaming for client {}: {}",
                          client.getUsername(),
                          ex.getMessage());
                    }
                  } catch (Exception ex) {
                    log.error("Error streaming : {}", ex.getMessage());
                  } finally {
                    if (inputStream != null) {
                      try {
                        inputStream.close();
                      } catch (IOException ex) {
                        log.warn("Error closing stream: {}", ex.getMessage());
                      }
                    }
                  }
                })
        .build();
  }

  /**
   * Build proxy redirect URL
   *
   * @param uriInfo Request URI info
   * @param client Client
   * @param url stream URL
   * @return Proxy URL
   */
  private String buildProxyUrl(UriInfo uriInfo, Client client, String url) {
    String encodedUrl = Base64.getUrlEncoder().encodeToString(url.getBytes());
    var baseUri = uriInfo.getBaseUri();
    String baseUrl =
        baseUri.getScheme()
            + "://"
            + baseUri.getHost()
            + (baseUri.getPort() > 0
                    && baseUri.getPort() != (baseUri.getScheme().equals("https") ? 443 : 80)
                ? ":" + baseUri.getPort()
                : "");

    return baseUrl
        + "/proxy/"
        + client.getUsername()
        + "/"
        + client.getPassword()
        + "?url="
        + encodedUrl;
  }
}
