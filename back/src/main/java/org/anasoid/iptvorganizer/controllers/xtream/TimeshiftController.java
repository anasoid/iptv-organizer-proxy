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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.dto.RequestType;
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

/**
 * Timeshift (Catch-up TV) Controller
 *
 * <p>Handles timeshift/archive stream requests for Xtream API: -
 * /timeshift/{username}/{password}/{duration}/{start}/{streamId}.{ext}
 *
 * <p>Supports archived broadcasts for streams that have tv_archive enabled.
 */
@Slf4j
@Path("")
@ApplicationScoped
public class TimeshiftController extends AbstractDataController {

  @Inject XtreamUserService xtreamUserService;
  @Inject ClientService clientService;
  @Inject ContentFilterService contentFilterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;

  /**
   * Handle timeshift stream request
   *
   * <p>Endpoint: GET /timeshift/{username}/{password}/{duration}/{start}/{streamId}.{ext}
   *
   * @param username Client username
   * @param password Client password
   * @param start Start timestamp (Unix time in seconds)
   * @param duration Duration in seconds
   * @param streamId Stream ID
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

      // Load stream — 404 if not found
      LiveStream stream = liveStreamService.findBySourceAndStreamId(source.getId(), streamIdInt);
      if (stream == null) {
        log.warn("Stream not found - source: {}, id: {}", source.getId(), streamId);
        return Response.status(Response.Status.NOT_FOUND).entity("Stream not found").build();
      }

      // Validate duration parameter
      try {
        Long.parseLong(duration);
      } catch (NumberFormatException e) {
        log.warn("Invalid duration format: {}", duration);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Invalid duration format")
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

      // Build timeshift URL and redirect
      String timeshiftUrl = buildTimeshiftUrl(source, streamId, start, duration, ext);
      ConnectXtreamStreamMode streamMode = clientService.resolveConnectXtreamStream(client, source);
      log.debug(
          "Timeshift request - user: {}, mode: {}, streamId: {}, sourceUrl: {}",
          username,
          streamMode,
          streamId,
          timeshiftUrl);

      return getStream(
          client,
          source,
          new HttpRequestDto(timeshiftUrl, RequestType.STREAM, httpHeaders),
          streamMode,
          uriInfo);

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
   * Timeshift always issues a redirect — no server-side streaming.
   *
   * <p>Overrides the default {@link AbstractDataController#getStream} which delegates to {@link
   * org.anasoid.iptvorganizer.utils.streaming.StreamModeHandler}.
   */
  @Override
  protected Response getStream(
      Client client,
      Source source,
      HttpRequestDto request,
      ConnectXtreamStreamMode streamMode,
      UriInfo uriInfo) {
    log.info("Timeshift redirect, mode: {}, url: {}", streamMode, request.getUrl());
    return Response.seeOther(URI.create(request.getUrl())).build();
  }

  /**
   * Build timeshift URL from source (Official Xtream API format)
   * /timeshift/{username}/{password}/{duration}/{start}/{streamId}.{ext}
   *
   * @param source The source
   * @param streamId Stream ID
   * @param startStr Start timestamp
   * @param durationStr Duration in seconds (already validated as a long)
   * @param ext File extension
   * @return Complete timeshift URL
   */
  private String buildTimeshiftUrl(
      Source source, String streamId, String startStr, String durationStr, String ext) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    long durationMinutes = Long.parseLong(durationStr);
    return String.format(
        "%s/timeshift/%s/%s/%d/%s/%s.%s",
        baseUrl,
        URLEncoder.encode(source.getUsername(), StandardCharsets.UTF_8),
        URLEncoder.encode(source.getPassword(), StandardCharsets.UTF_8),
        durationMinutes,
        startStr,
        streamId,
        ext);
  }
}
