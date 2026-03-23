package org.anasoid.iptvorganizer.controllers.admin.stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository;
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
      @QueryParam("sortBy") String sortBy,
      @QueryParam("sortDir") String sortDir,
      @QueryParam("addedDateFrom") String addedDateFrom,
      @QueryParam("addedDateTo") String addedDateTo,
      @QueryParam("createdDateFrom") String createdDateFrom,
      @QueryParam("createdDateTo") String createdDateTo,
      @QueryParam("updateDateFrom") String updateDateFrom,
      @QueryParam("updateDateTo") String updateDateTo,
      @QueryParam("releaseDateFrom") String releaseDateFrom,
      @QueryParam("releaseDateTo") String releaseDateTo,
      @QueryParam("ratingMin") Double ratingMin,
      @QueryParam("ratingMax") Double ratingMax,
      @QueryParam("tmdbMin") Long tmdbMin,
      @QueryParam("tmdbMax") Long tmdbMax,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      throw new ValidationException("sourceId is required");
    }

    if (type == null || type.isBlank()) {
      throw new ValidationException("type is required (live, vod, or series)");
    }

    validateSort(sortBy, sortDir);
    validateRanges(ratingMin, ratingMax);

    BaseStreamRepository.StreamQueryOptions options =
        new BaseStreamRepository.StreamQueryOptions(
            categoryId,
            search,
            streamId,
            sortBy,
            sortDir,
            parseDate(addedDateFrom, "addedDateFrom"),
            parseDate(addedDateTo, "addedDateTo"),
            parseDate(createdDateFrom, "createdDateFrom"),
            parseDate(createdDateTo, "createdDateTo"),
            parseDate(updateDateFrom, "updateDateFrom"),
            parseDate(updateDateTo, "updateDateTo"),
            parseDate(releaseDateFrom, "releaseDateFrom"),
            parseDate(releaseDateTo, "releaseDateTo"),
            ratingMin,
            ratingMax);

    List<? extends BaseStream> streams = Collections.emptyList();
    long total = 0;

    switch (type.toLowerCase()) {
      case "live":
        streams = liveStreamService.findBySourceIdPagedWithFilters(sourceId, options, page, limit);
        total = liveStreamService.countBySourceIdWithFilters(sourceId, options);
        break;
      case "vod":
        streams = vodStreamService.findBySourceIdPagedWithFilters(sourceId, options, page, limit);
        total = vodStreamService.countBySourceIdWithFilters(sourceId, options);
        break;
      case "series":
        streams = seriesService.findBySourceIdPagedWithFilters(sourceId, options, page, limit);
        total = seriesService.countBySourceIdWithFilters(sourceId, options);
        break;
      default:
        throw new ValidationException("Invalid type. Must be 'live', 'vod', or 'series'");
    }

    return ResponseUtils.okWithPagination(streams, PaginationMeta.of(page, limit, total));
  }

  private void validateSort(String sortBy, String sortDir) {
    if (sortBy == null || sortBy.isBlank()) {
      return;
    }
    List<String> supportedSortBy =
        List.of("addedDate", "createdAt", "updatedAt", "releaseDate", "rating", "tmdb");
    if (!supportedSortBy.contains(sortBy)) {
      throw new ValidationException(
          "Invalid sortBy. Must be one of: addedDate, createdAt, updatedAt, releaseDate, rating, tmdb");
    }
    if (sortDir != null
        && !sortDir.isBlank()
        && !"asc".equalsIgnoreCase(sortDir)
        && !"desc".equalsIgnoreCase(sortDir)) {
      throw new ValidationException("Invalid sortDir. Must be 'asc' or 'desc'");
    }
  }

  private void validateRanges(Double ratingMin, Double ratingMax) {
    if (ratingMin != null && ratingMax != null && ratingMin > ratingMax) {
      throw new ValidationException("ratingMin cannot be greater than ratingMax");
    }
  }

  private LocalDate parseDate(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (Exception e) {
      throw new ValidationException("Invalid " + fieldName + ". Expected format: YYYY-MM-DD");
    }
  }

  /** Get stream by ID GET /api/streams/:id?type= */
  @GET
  @Path("/{id}")
  public Response getStream(@PathParam("id") Long id, @QueryParam("type") String type) {
    if (type == null || type.isBlank()) {
      throw new ValidationException("type is required (live, vod, or series)");
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
        throw new ValidationException("Invalid type. Must be 'live', 'vod', or 'series'");
    }

    if (stream == null) {
      throw new NotFoundException("Stream not found with ID: " + id + " and type: " + type);
    }
    return ResponseUtils.ok(stream);
  }

  /** Update stream allow-deny status PATCH /api/streams/:id/allow-deny?type= */
  @PATCH
  @Path("/{id}/allow-deny")
  public Response updateStreamAllowDeny(
      @PathParam("id") Long id,
      @QueryParam("type") String type,
      java.util.Map<String, Object> request) {
    if (type == null || type.isBlank()) {
      throw new ValidationException("type is required (live, vod, or series)");
    }

    String allowDeny =
        request != null && request.get("allowDeny") != null
            ? String.valueOf(request.get("allowDeny"))
            : null;

    if (allowDeny != null && !allowDeny.isEmpty() && !allowDeny.equals("null")) {
      if (!allowDeny.equals("allow") && !allowDeny.equals("deny")) {
        throw new ValidationException("allowDeny must be 'allow', 'deny', or null");
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
        throw new ValidationException("Invalid type. Must be 'live', 'vod', or 'series'");
    }

    if (stream == null) {
      throw new NotFoundException("Stream not found with ID: " + id + " and type: " + type);
    }
    return ResponseUtils.ok(stream);
  }
}
