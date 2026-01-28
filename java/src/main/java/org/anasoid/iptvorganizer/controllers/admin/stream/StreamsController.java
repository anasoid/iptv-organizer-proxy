package org.anasoid.iptvorganizer.controllers.admin.stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Streams controller Handles Live, VOD, and Series streams */
@Path("/api/streams")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class StreamsController extends BaseController {

  @Inject LiveStreamService liveStreamService;

  @Inject VodStreamService vodStreamService;

  @Inject SeriesService seriesService;

  /**
   * Get streams with filters GET
   * /api/streams?sourceId=&type=&page=1&limit=20&categoryId=&search=&streamId=
   */
  @GET
  public Response getStreams(
      @QueryParam("sourceId") Long sourceId,
      @QueryParam("type") String type,
      @QueryParam("categoryId") Integer categoryId,
      @QueryParam("search") String search,
      @QueryParam("streamId") Integer streamId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      return ResponseUtils.badRequest("sourceId is required");
    }

    if (type == null || type.isBlank()) {
      return ResponseUtils.badRequest("type is required (live, vod, or series)");
    }

    try {
      List<? extends BaseStream> streams = Collections.emptyList();

      // Fetch streams by type
      switch (type.toLowerCase()) {
        case "live":
          if (categoryId != null) {
            streams = liveStreamService.findBySourceAndCategory(sourceId, categoryId, limit);
          } else if (streamId != null) {
            var stream = liveStreamService.findBySourceAndStreamId(sourceId, streamId);
            streams = stream != null ? List.of(stream) : Collections.emptyList();
          } else {
            streams = liveStreamService.findBySourceId(sourceId);
          }
          break;
        case "vod":
          if (categoryId != null) {
            streams = vodStreamService.findBySourceAndCategory(sourceId, categoryId, limit);
          } else if (streamId != null) {
            var stream = vodStreamService.findBySourceAndStreamId(sourceId, streamId);
            streams = stream != null ? List.of(stream) : Collections.emptyList();
          } else {
            streams = vodStreamService.findBySourceId(sourceId);
          }
          break;
        case "series":
          if (categoryId != null) {
            streams = seriesService.findBySourceAndCategory(sourceId, categoryId, limit);
          } else if (streamId != null) {
            var stream = seriesService.findBySourceAndStreamId(sourceId, streamId);
            streams = stream != null ? List.of(stream) : Collections.emptyList();
          } else {
            streams = seriesService.findBySourceId(sourceId);
          }
          break;
        default:
          return ResponseUtils.badRequest("Invalid type. Must be 'live', 'vod', or 'series'");
      }

      // Apply search filter if provided
      if (search != null && !search.isBlank()) {
        final String searchTerm = search.toLowerCase();
        streams =
            streams.stream()
                .filter(
                    s ->
                        s.getName().toLowerCase().contains(searchTerm)
                            || (s.getLabels() != null
                                && s.getLabels().toLowerCase().contains(searchTerm)))
                .collect(Collectors.toList());
      }

      // Apply pagination
      long total = streams.size();
      int startIdx = (page - 1) * limit;
      int endIdx = Math.min(startIdx + limit, (int) total);
      List<? extends BaseStream> paginatedStreams =
          startIdx < streams.size() ? streams.subList(startIdx, endIdx) : Collections.emptyList();

      return ResponseUtils.okWithPagination(
          paginatedStreams, PaginationMeta.of(page, limit, total));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch streams: " + ex.getMessage());
    }
  }

  /** Get stream by ID GET /api/streams/:id?type= */
  @GET
  @Path("/{id}")
  public Response getStream(@PathParam("id") Long id, @QueryParam("type") String type) {
    try {
      if (type == null || type.isBlank()) {
        return ResponseUtils.badRequest("type is required (live, vod, or series)");
      }

      BaseStream stream = null;

      switch (type.toLowerCase()) {
        case "live":
          stream = liveStreamService.getById(id);
          break;
        case "vod":
          stream = vodStreamService.getById(id);
          break;
        case "series":
          stream = seriesService.getById(id);
          break;
        default:
          return ResponseUtils.badRequest("Invalid type. Must be 'live', 'vod', or 'series'");
      }

      if (stream != null) {
        return ResponseUtils.ok(stream);
      } else {
        return ResponseUtils.notFound("Stream not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch stream: " + ex.getMessage());
    }
  }

  /** Update stream allow-deny status PATCH /api/streams/:id/allow-deny?type= */
  @PATCH
  @Path("/{id}/allow-deny")
  public Response updateStreamAllowDeny(
      @PathParam("id") Long id,
      @QueryParam("type") String type,
      java.util.Map<String, Object> request) {
    try {
      if (type == null || type.isBlank()) {
        return ResponseUtils.badRequest("type is required (live, vod, or series)");
      }

      String allowDeny =
          request != null && request.get("allowDeny") != null
              ? String.valueOf(request.get("allowDeny"))
              : null;

      if (allowDeny != null && !allowDeny.isEmpty() && !allowDeny.equals("null")) {
        if (!allowDeny.equals("allow") && !allowDeny.equals("deny")) {
          return ResponseUtils.badRequest("allowDeny must be 'allow', 'deny', or null");
        }
      } else {
        allowDeny = null;
      }

      BaseStream stream = null;

      switch (type.toLowerCase()) {
        case "live":
          var liveStream = liveStreamService.getById(id);
          if (liveStream != null) {
            liveStream.setAllowDeny(
                allowDeny != null ? BaseStream.AllowDenyStatus.fromValue(allowDeny) : null);
            liveStreamService.update(liveStream);
            stream = liveStream;
          }
          break;
        case "vod":
          var vodStream = vodStreamService.getById(id);
          if (vodStream != null) {
            vodStream.setAllowDeny(
                allowDeny != null ? BaseStream.AllowDenyStatus.fromValue(allowDeny) : null);
            vodStreamService.update(vodStream);
            stream = vodStream;
          }
          break;
        case "series":
          var series = seriesService.getById(id);
          if (series != null) {
            series.setAllowDeny(
                allowDeny != null ? BaseStream.AllowDenyStatus.fromValue(allowDeny) : null);
            seriesService.update(series);
            stream = series;
          }
          break;
        default:
          return ResponseUtils.badRequest("Invalid type. Must be 'live', 'vod', or 'series'");
      }

      if (stream != null) {
        return ResponseUtils.ok(stream);
      } else {
        return ResponseUtils.notFound("Stream not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update stream: " + ex.getMessage());
    }
  }
}
