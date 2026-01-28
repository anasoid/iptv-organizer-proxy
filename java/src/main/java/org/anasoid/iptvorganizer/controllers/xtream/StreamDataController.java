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
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
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
    return handleStreamRequest(username, password, streamId, ext, "live", uriInfo, httpHeaders);
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
    return handleStreamRequest(username, password, streamId, ext, "movie", uriInfo, httpHeaders);
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
    return handleStreamRequest(username, password, streamId, ext, "series", uriInfo, httpHeaders);
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
      HttpHeaders httpHeaders) {

    try {
      // Validate and authenticate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      // NEW: Validate stream access
      if (!hasStreamAccess(client, source, streamId, streamType)) {
        log.warn("Client {} attempted to access blocked stream: {}", username, streamId);
        return Response.status(Response.Status.FORBIDDEN).build();
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

    } catch (UnauthorizedException ex) {
      log.warn("Stream request unauthorized", ex);
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (ForbiddenException ex) {
      log.warn("Stream request forbidden", ex);
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception ex) {
      log.error("Error handling stream request", ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private Response getStream(Client client, Source source, String streamUrl, UriInfo uriInfo) {
    // Check useRedirect setting with priority: client -> source -> environment

    // If useRedirect is enabled, return direct 302 redirect to original URL
    boolean disableStreamProxy = clientService.resolveDisableStreamProxy(client, source);
    boolean useRedirect = clientService.resolveUseRedirect(client, source);
    if (disableStreamProxy) {
      // Direct 302 redirect to source
      // Note: Client's player will follow any redirects automatically
      log.info("Direct stream proxy disabled, returning 302 redirect to: {}", streamUrl);
      if (useRedirect) {
        log.info("useRedirect enabled, returning direct 302 redirect to: {}", streamUrl);
        return Response.seeOther(URI.create(streamUrl)).build();
      } else {
        log.info("useRedirect enabled, returning direct 302 redirect to: {}", streamUrl);
        return Response.seeOther(URI.create(streamUrl)).build();
      }
    } else {

      // Otherwise, encode URL and redirect to our proxy endpoint
      // Our proxy endpoint will handle redirect following automatically via HttpClient
      String encodedUrl = Base64.getUrlEncoder().encodeToString(streamUrl.getBytes());
      String proxyUrl =
          buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), encodedUrl);

      log.info("Stream proxy enabled, redirecting to proxy: {}", proxyUrl);
      return Response.seeOther(URI.create(proxyUrl)).build();
    }
  }

  private Response getStream(
      Client client, Source source, String streamUrl, UriInfo uriInfo, HttpHeaders httpHeaders) {
    boolean disableStreamProxy = clientService.resolveDisableStreamProxy(client, source);
    boolean useRedirect = clientService.resolveUseRedirect(client, source);

    // Case 1: disableStreamProxy=true AND useRedirect=false
    // Hide credentials by checking for upstream redirect
    if (disableStreamProxy) {
      if (useRedirect) {

        return Response.seeOther(URI.create(streamUrl)).build();
      } else {

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
      }
    }
    // Case 3: disableStreamProxy=false
    // Use proxy mode - encode URL and redirect to /proxy endpoint
    log.info("Proxy mode - encoding URL and redirecting to proxy");
    String encodedUrl = Base64.getUrlEncoder().encodeToString(streamUrl.getBytes());
    String proxyUrl =
        buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), encodedUrl);
    return Response.seeOther(URI.create(proxyUrl)).build();
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
   * @param encodedUrl Base64 encoded stream URL
   * @return Proxy URL
   */
  private String buildProxyUrl(
      UriInfo uriInfo, String username, String password, String encodedUrl) {
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
      Integer streamIdInt;
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
    switch (streamType.toLowerCase()) {
      case "live":
        return liveStreamService.findBySourceAndStreamId(sourceId, streamId);
      case "movie":
      case "vod":
        return vodStreamService.findBySourceAndStreamId(sourceId, streamId);
      case "series":
        return seriesService.findBySourceAndStreamId(sourceId, streamId);
      default:
        return null;
    }
  }

  /**
   * Get category type string from stream type
   *
   * @param streamType Stream type (live, movie, series)
   * @return Category type string
   */
  private String getStreamTypeCategory(String streamType) {
    switch (streamType.toLowerCase()) {
      case "live":
        return "live";
      case "movie":
      case "vod":
        return "vod";
      case "series":
        return "series";
      default:
        return streamType.toLowerCase();
    }
  }
}
