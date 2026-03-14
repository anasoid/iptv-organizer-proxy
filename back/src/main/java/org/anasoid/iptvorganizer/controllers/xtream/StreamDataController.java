package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.models.http.RedirectCheckResult;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.services.xtream.ContentFilterService;
import org.anasoid.iptvorganizer.services.xtream.FilterContext;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.StreamProxyHttpClient;

/**
 * Stream Data Controller
 *
 * <p>Handles stream proxying for Xtream API: - /live/{username}/{password}/{streamId}.{ext} -
 * /movie/{username}/{password}/{streamId}.{ext} - /series/{username}/{password}/{streamId}.{ext}
 *
 * <p>Implements dual proxy architecture: 1. Direct 302 redirect if `disablestreamproxy=true` 2.
 * Base64 URL encoding and redirect to `/proxy` if `disablestreamproxy=false`
 */
@Slf4j
@Path("")
@ApplicationScoped
public class StreamDataController {

  @Inject XtreamUserService xtreamUserService;
  @Inject ClientService clientService;
  @Inject ContentFilterService contentFilterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject StreamProxyHttpClient streamProxyHttpClient;

  /**
   * Handle live stream request
   *
   * <p>Endpoint: GET /live/{username}/{password}/{streamId}.{ext}
   *
   * @param username Client username
   * @param password Client password
   * @param streamId Stream ID
   * @param ext File extension (m3u8, ts, etc)
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Stream response or redirect
   */
  @GET
  @Path("/live/{username}/{password}/{streamId}.{ext}")
  public Response handleLiveStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo,
      @Context HttpHeaders httpHeaders) {
    return handleStreamRequest(
        username, password, streamId, ext, "live", uriInfo, httpHeaders, true);
  }

  /**
   * Handle VOD stream request
   *
   * <p>Endpoint: GET /movie/{username}/{password}/{streamId}.{ext}
   *
   * @param username Client username
   * @param password Client password
   * @param streamId Stream ID
   * @param ext File extension
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Stream response or redirect
   */
  @GET
  @Path("/movie/{username}/{password}/{streamId}.{ext}")
  public Response handleVodStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo,
      @Context HttpHeaders httpHeaders) {
    return handleStreamRequest(
        username, password, streamId, ext, "movie", uriInfo, httpHeaders, true);
  }

  /**
   * Handle series stream request
   *
   * <p>Endpoint: GET /series/{username}/{password}/{streamId}.{ext}
   *
   * @param username Client username
   * @param password Client password
   * @param streamId Stream ID
   * @param ext File extension
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Stream response or redirect
   */
  @GET
  @Path("/series/{username}/{password}/{streamId}.{ext}")
  public Response handleSeriesStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo,
      @Context HttpHeaders httpHeaders) {
    return handleStreamRequest(
        username, password, streamId, ext, "series", uriInfo, httpHeaders, false);
  }

  /**
   * Handle stream request with dual proxy logic
   *
   * @param username Client username
   * @param password Client password
   * @param streamId Stream ID
   * @param ext File extension
   * @param streamType Stream type (live, movie, series)
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Response with stream or redirect
   */
  private Response handleStreamRequest(
      String username,
      String password,
      String streamId,
      String ext,
      String streamType,
      UriInfo uriInfo,
      HttpHeaders httpHeaders,
      boolean checkAccess) {

    // Validate and authenticate client (throws UnauthorizedException if invalid)
    var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
    Client client = authResult.getClient();
    Source source = authResult.getSource();

    // Validate stream access
    if (checkAccess && !hasStreamAccess(client, source, streamId, streamType)) {
      log.warn("Client {} attempted to access blocked stream: {}", username, streamId);
      throw new ForbiddenException("Access to stream " + streamId + " is blocked");
    }

    // Build stream URL from source
    String streamUrl = buildStreamUrl(source, streamType, streamId, ext);

    log.info(
        "Stream request - user: {}, type: {}, streamId: {}, sourceUrl: {}",
        username,
        streamType,
        streamId,
        streamUrl);

    return getStream(client, source, streamUrl, uriInfo, httpHeaders);
  }

  private Response getStream(
      Client client, Source source, String streamUrl, UriInfo uriInfo, HttpHeaders httpHeaders) {
    ConnectXtreamStreamMode streamMode = clientService.resolveConnectXtreamStream(client, source);

    switch (streamMode) {
      case REDIRECT:
        log.info("Redirect mode - sending 302 redirect to upstream");
        return Response.seeOther(URI.create(streamUrl)).build();

      case DIRECT:
        log.info("Direct mode - checking for upstream redirect to hide credentials");
        RedirectCheckResult redirectCheck =
            streamProxyHttpClient.checkForRedirect(streamUrl, client, source, httpHeaders);
        if (redirectCheck.isError()) {
          return Response.status(Response.Status.BAD_GATEWAY)
              .entity("Upstream redirect check failed")
              .build();
        }

        if (redirectCheck.isRedirect()) {
          log.info("Returning upstream redirect location: {}", redirectCheck.getLocation());
          return Response.seeOther(URI.create(redirectCheck.getLocation())).build();
        } else {
          // Upstream does NOT redirect - cannot hide credentials
          log.warn(
              "Upstream does not redirect, cannot hide credentials. Status: {}",
              redirectCheck.getStatusCode());
          return Response.status(Response.Status.BAD_GATEWAY)
              .entity("Upstream does not provide credential-free redirect")
              .build();
        }

      case PROXY:
        log.info("Proxy mode - encoding URL and redirecting to proxy");
        String proxyUrl =
            buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), streamUrl);
        return Response.seeOther(URI.create(proxyUrl)).build();

      case TUNNEL:
        log.info("Tunnel mode - using application-level tunneling");
        // TODO: Implement tunnel mode
        // For now, fall back to proxy mode
        String proxyUrlTunnel =
            buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), streamUrl);
        return Response.seeOther(URI.create(proxyUrlTunnel)).build();

      case DEFAULT:
        // Should not happen - default should be resolved by service
        log.warn("Unexpected DEFAULT mode in getStream");
        String proxyUrlDefault =
            buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), streamUrl);
        return Response.seeOther(URI.create(proxyUrlDefault)).build();

      default:
        log.warn("Unknown stream mode: {}", streamMode);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("Unknown stream mode")
            .build();
    }
  }

  /**
   * Build stream URL from source
   *
   * @param source The source
   * @param streamType Stream type (live, movie, series)
   * @param streamId Stream ID
   * @param ext File extension
   * @return Complete stream URL
   */
  private String buildStreamUrl(Source source, String streamType, String streamId, String ext) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    return String.format(
        "%s/%s/%s/%s/%s.%s",
        baseUrl, streamType, source.getUsername(), source.getPassword(), streamId, ext);
  }

  /**
   * Build proxy redirect URL
   *
   * @param uriInfo Request URI info
   * @param username Client username
   * @param password Client password
   * @param url stream URL
   * @return Proxy URL
   */
  private String buildProxyUrl(UriInfo uriInfo, String username, String password, String url) {
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

    return baseUrl + "/proxy/" + username + "/" + password + "?url=" + encodedUrl;
  }

  /**
   * Check if client has access to specific stream
   *
   * @param client The authenticated client
   * @param source The source
   * @param streamId Stream ID to check
   * @param streamType Stream type (live, movie, series)
   * @return true if stream access is allowed
   */
  private boolean hasStreamAccess(
      Client client, Source source, String streamId, String streamType) {
    try {
      // Parse stream ID as integer
      int streamIdInt;
      try {
        streamIdInt = Integer.parseInt(streamId);
      } catch (NumberFormatException e) {
        log.warn("Invalid stream ID format: {}", streamId);
        return false;
      }

      // Load stream from database
      BaseStream stream = loadStream(source.getId(), streamIdInt, streamType);
      if (stream == null) {
        log.warn(
            "Stream not found - source: {}, type: {}, id: {}",
            source.getId(),
            streamType,
            streamId);
        return false; // Stream not found
      }

      // Load category
      String streamTypeCategory = getStreamTypeCategory(streamType);
      Category category =
          categoryService.findBySourceAndCategoryId(
              source.getId(), stream.getCategoryId(), streamTypeCategory);

      // Build filtering context and check access
      FilterContext context = contentFilterService.buildFilterContext(client);
      return contentFilterService.shouldIncludeStream(context, stream, category);
    } catch (Exception e) {
      log.error("Error checking stream access", e);
      return false; // Deny access on error
    }
  }

  /**
   * Load stream from database by type
   *
   * @param sourceId Source ID
   * @param streamId Stream ID
   * @param streamType Stream type (live, movie, series)
   * @return Stream or null if not found
   */
  private BaseStream loadStream(Long sourceId, Integer streamId, String streamType) {
    return switch (streamType.toLowerCase()) {
      case "live" -> liveStreamService.findBySourceAndStreamId(sourceId, streamId);
      case "movie", "vod" -> vodStreamService.findBySourceAndStreamId(sourceId, streamId);
      case "series" -> seriesService.findBySourceAndStreamId(sourceId, streamId);
      default -> null;
    };
  }

  /**
   * Get category type string from stream type
   *
   * @param streamType Stream type (live, movie, series)
   * @return Category type string
   */
  private String getStreamTypeCategory(String streamType) {
    return switch (streamType.toLowerCase()) {
      case "live" -> "live";
      case "movie", "vod" -> "vod";
      case "series" -> "series";
      default -> streamType.toLowerCase();
    };
  }
}
