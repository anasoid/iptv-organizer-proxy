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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.xtream.ContentFilterService;
import org.anasoid.iptvorganizer.services.xtream.FilterContext;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.StreamModeHandler;

/**
 * Timeshift (Catch-up TV) Controller
 *
 * <p>Handles timeshift/archive stream requests for Xtream API: -
 * /timeshift/{username}/{password}/{streamId}/{start}/{duration}.{ext}
 *
 * <p>Supports archived broadcasts for streams that have tv_archive enabled.
 */
@Slf4j
@Path("")
@ApplicationScoped
public class TimeshiftController {

  @Inject XtreamUserService xtreamUserService;
  @Inject ClientService clientService;
  @Inject ContentFilterService contentFilterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject StreamModeHandler streamModeHandler;

  /**
   * Handle timeshift stream request
   *
   * <p>Endpoint: GET /timeshift/{username}/{password}/{streamId}/{start}/{duration}.{ext}
   *
   * @param username Client username
   * @param password Client password
   * @param streamId Stream ID
   * @param start Start timestamp (Unix time in seconds)
   * @param duration Duration in seconds
   * @param ext File extension (ts, m3u8, etc)
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Stream response or redirect
   */
  @GET
  @Path("/timeshift/{username}/{password}/{duration}/{start}/{streamId}.{ext}")
  public Response handleTimeshiftStream(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @PathParam("start") String start,
      @PathParam("duration") String duration,
      @PathParam("streamId") String streamId,
      @PathParam("ext") String ext,
      @Context UriInfo uriInfo,
      @Context HttpHeaders httpHeaders) {

    try {
      // Authenticate and validate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      // Parse and validate stream ID
      int streamIdInt;
      try {
        streamIdInt = Integer.parseInt(streamId);
      } catch (NumberFormatException e) {
        log.warn("Invalid stream ID format: {}", streamId);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Invalid stream ID format")
            .build();
      }

      // Load live stream from database
      LiveStream stream = liveStreamService.findBySourceAndStreamId(source.getId(), streamIdInt);
      if (stream == null) {
        log.warn("Stream not found - source: {}, id: {}", source.getId(), streamId);
        return Response.status(Response.Status.NOT_FOUND).entity("Stream not found").build();
      }

      // Validate timeshift parameters
      TimeshiftValidationResult validation = validateTimeshiftParameters(start, duration, stream);
      if (!validation.isValid()) {
        log.warn("Invalid timeshift parameters: {}", validation.getErrorMessage());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(validation.getErrorMessage())
            .build();
      }

      // Check content filtering
      Category category =
          categoryService.findBySourceAndCategoryId(source.getId(), stream.getCategoryId(), "live");
      FilterContext context = contentFilterService.buildFilterContext(client);
      if (!contentFilterService.shouldIncludeStream(context, stream, category)) {
        log.warn("Client {} access denied to timeshift stream: {}", username, streamId);
        throw new ForbiddenException("Access to stream " + streamId + " is blocked");
      }

      // Build timeshift URL from source
      String timeshiftUrl = buildTimeshiftUrl(source, streamIdInt, start, duration, ext);
      ConnectXtreamStreamMode streamMode = clientService.resolveConnectXtreamStream(client, source);

      log.info(
          "Timeshift request - user: {},mode: {},  , streamId: {}, sourceUrl: {}",
          username,
          streamMode,
          streamId,
          timeshiftUrl);

      return getStream(client, source, timeshiftUrl, streamMode, uriInfo, httpHeaders);

    } catch (UnauthorizedException ex) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (ForbiddenException ex) {
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (Exception e) {
      log.error("Error handling timeshift stream request", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Internal server error")
          .build();
    }
  }

  /**
   * Get stream response using configured proxy mode
   *
   * @param client The authenticated client
   * @param source The source
   * @param streamUrl The stream URL to proxy
   * @param uriInfo Request URI info
   * @param httpHeaders Client request headers
   * @return Response with stream or redirect
   */
  private Response getStream(
      Client client,
      Source source,
      String streamUrl,
      ConnectXtreamStreamMode streamMode,
      UriInfo uriInfo,
      HttpHeaders httpHeaders) {
    // Timeshift always issues a redirect - no server-side streaming
    return switch (streamMode) {
      case PROXY -> {
        log.info("Proxy mode - redirecting timeshift via proxy");
        String encodedUrl = java.util.Base64.getUrlEncoder().encodeToString(streamUrl.getBytes());
        String proxyUrl =
            buildProxyUrl(uriInfo, client.getUsername(), client.getPassword(), encodedUrl);
        yield Response.seeOther(java.net.URI.create(proxyUrl)).build();
      }
      default -> {
        log.info("Redirecting timeshift to upstream URL, mode: {}", streamMode);
        yield Response.seeOther(java.net.URI.create(streamUrl)).build();
      }
    };
  }

  /**
   * Build timeshift URL from source (Official Xtream API format)
   * /timeshift/{username}/{password}/{duration}/{start}/{streamId}.{ext}
   *
   * @param source The source
   * @param streamId Stream ID
   * @param startStr Start timestamp as Unix time in seconds
   * @param durationStr Duration in seconds
   * @param ext File extension (not used in official API but kept for compatibility)
   * @return Complete timeshift URL with query parameters
   */
  private String buildTimeshiftUrl(
      Source source, int streamId, String startStr, String durationStr, String ext) {
    String baseUrl = source.getUrl().replaceAll("/$", "");

    // Convert duration from seconds to minutes
    long durationMinutes = Long.parseLong(durationStr);

    // Build official Xtream API format URL: /streaming/timeshift.php
    String url =
        String.format(
            "%s/timeshift/%s/%s/%d/%s/%d.%s",
            baseUrl,
            URLEncoder.encode(source.getUsername(), StandardCharsets.UTF_8),
            URLEncoder.encode(source.getPassword(), StandardCharsets.UTF_8),
            durationMinutes,
            startStr,
            streamId,
            ext);

    return url;
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
   * Validate timeshift parameters
   *
   * @param startStr Start timestamp as string (Unix time in seconds)
   * @param durationStr Duration as string (in seconds)
   * @param stream The live stream
   * @return Validation result with error message if invalid
   */
  private TimeshiftValidationResult validateTimeshiftParameters(
      String startStr, String durationStr, LiveStream stream) {

    // Parse duration
    long duration;
    try {
      duration = Long.parseLong(durationStr);
    } catch (NumberFormatException e) {
      return TimeshiftValidationResult.invalid("Invalid duration format");
    }

    // Validate duration is positive and not too long
    if (duration <= 0) {
      return TimeshiftValidationResult.invalid("Duration must be positive");
    }

    if (duration > 86400) { // 24 hours
      return TimeshiftValidationResult.invalid("Duration must not exceed 24 hours");
    }

    return TimeshiftValidationResult.valid();
  }

  /** Validation result for timeshift parameters */
  private static class TimeshiftValidationResult {
    private final boolean valid;
    private final String errorMessage;

    private TimeshiftValidationResult(boolean valid, String errorMessage) {
      this.valid = valid;
      this.errorMessage = errorMessage;
    }

    static TimeshiftValidationResult valid() {
      return new TimeshiftValidationResult(true, null);
    }

    static TimeshiftValidationResult invalid(String errorMessage) {
      return new TimeshiftValidationResult(false, errorMessage);
    }

    boolean isValid() {
      return valid;
    }

    String getErrorMessage() {
      return errorMessage;
    }
  }
}
