package org.anasoid.iptvorganizer.utils.streaming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.RedirectCheckResult;
import org.anasoid.iptvorganizer.utils.TunnelUtils;

@Slf4j
@ApplicationScoped
public class StreamModeHandler {

  @Inject private StreamProxyHttpClient streamProxyHttpClient;
  @Inject TunnelUtils tunnelUtils;

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
   * @param request The HTTP request DTO containing url and headers
   * @param client The authenticated client
   * @param source The source
   * @return Response with redirect or error
   */
  public Response handleDirectMode(HttpRequestDto request, Client client, Source source) {
    RedirectCheckResult redirectCheck =
        streamProxyHttpClient.checkForRedirect(request, client, source);

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
   * @param request The HTTP request DTO containing url and headers
   * @param client Client
   * @param source Source
   * @return Streaming response
   */
  public Response handleTunnelMode(HttpRequestDto request, Client client, Source source) {
    return handleTunnelMode(request, client, source, 4 * 3600 * 1000);
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
   * Handle TUNNEL mode with explicit timeout
   *
   * @param request The HTTP request DTO containing url and headers
   * @param client Client
   * @param source Source
   * @param timeout Timeout in milliseconds
   * @return Streaming response
   */
  public Response handleTunnelMode(
      HttpRequestDto request, Client client, Source source, long timeout) {
    return tunnelUtils.streamFromUpstream(
        request,
        client,
        source,
        tunnelUtils.buildHttpOptions(client, source).timeout(timeout).build());
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
