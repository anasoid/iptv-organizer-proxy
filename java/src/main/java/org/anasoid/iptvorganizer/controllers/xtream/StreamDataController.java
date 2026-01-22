package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.services.xtream.ContentFilterService;
import org.anasoid.iptvorganizer.services.xtream.FilterContext;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;

/**
 * Stream Data Controller
 *
 * <p>Handles stream proxying for Xtream API: - /live/{username}/{password}/{streamId}.{ext} -
 * /movie/{username}/{password}/{streamId}.{ext} - /series/{username}/{password}/{streamId}.{ext}
 *
 * <p>Implements dual proxy architecture: 1. Direct 302 redirect if `disablestreamproxy=true` 2.
 * Base64 URL encoding and redirect to `/proxy` if `disablestreamproxy=false`
 */
@Path("")
@ApplicationScoped
public class StreamDataController {

  private static final Logger LOGGER = Logger.getLogger(StreamDataController.class.getName());

  @Inject XtreamUserService xtreamUserService;
  @Inject HttpStreamingService httpStreamingService;
  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject ContentFilterService contentFilterService;
  @Inject FilterService filterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;

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
   * @return Stream response or redirect
   */
  @GET
  @Path("/live/{username}/{password}/{streamId}.{ext}")
  public Response handleLiveStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo) {
    return handleStreamRequest(username, password, streamId, ext, "live", uriInfo);
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
   * @return Stream response or redirect
   */
  @GET
  @Path("/movie/{username}/{password}/{streamId}.{ext}")
  public Response handleVodStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo) {
    return handleStreamRequest(username, password, streamId, ext, "movie", uriInfo);
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
   * @return Stream response or redirect
   */
  @GET
  @Path("/series/{username}/{password}/{streamId}.{ext}")
  public Response handleSeriesStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo) {
    return handleStreamRequest(username, password, streamId, ext, "series", uriInfo);
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
   * @return Response with stream or redirect
   */
  private Response handleStreamRequest(
      String username,
      String password,
      String streamId,
      String ext,
      String streamType,
      UriInfo uriInfo) {

    try {
      // Validate and authenticate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      // NEW: Validate stream access
      if (!hasStreamAccess(client, source, streamId, streamType)) {
        LOGGER.warning("Client " + username + " attempted to access blocked stream: " + streamId);
        return Response.status(Response.Status.FORBIDDEN).build();
      }

      // Build stream URL from source
      String streamUrl = buildStreamUrl(source, streamType, streamId, ext);

      LOGGER.info(
          String.format(
              "Stream request - user: %s, type: %s, streamId: %s, sourceUrl: %s",
              username, streamType, streamId, streamUrl));

      // Check if we should disable proxy (return direct 302)
      Boolean disableStreamProxy = source.getDisableStreamProxy();
      if (disableStreamProxy != null && disableStreamProxy) {
        // Direct 302 redirect to source
        LOGGER.info("Direct stream proxy disabled, returning 302 redirect to: " + streamUrl);
        return Response.seeOther(java.net.URI.create(streamUrl)).build();
      }

      // Otherwise, encode URL and redirect to our proxy endpoint
      String encodedUrl = Base64.getUrlEncoder().encodeToString(streamUrl.getBytes());
      String proxyUrl = buildProxyUrl(uriInfo, username, password, encodedUrl);

      LOGGER.info("Stream proxy enabled, redirecting to proxy: " + proxyUrl);
      return Response.seeOther(java.net.URI.create(proxyUrl)).build();

    } catch (UnauthorizedException ex) {
      LOGGER.warning("Stream request unauthorized: " + ex.getMessage());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (ForbiddenException ex) {
      LOGGER.warning("Stream request forbidden: " + ex.getMessage());
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception ex) {
      LOGGER.severe("Error handling stream request: " + ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
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
        LOGGER.warning("Invalid stream ID format: " + streamId);
        return false;
      }

      // Load stream from database
      BaseStream stream = loadStream(source.getId(), streamIdInt, streamType);
      if (stream == null) {
        LOGGER.warning(
            "Stream not found - source: "
                + source.getId()
                + ", type: "
                + streamType
                + ", id: "
                + streamId);
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
      LOGGER.log(Level.SEVERE, "Error checking stream access: ", e);
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
