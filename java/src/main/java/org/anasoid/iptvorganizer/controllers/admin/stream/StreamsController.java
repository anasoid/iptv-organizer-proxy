package org.anasoid.iptvorganizer.controllers.admin.stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Streams controller Handles Live, VOD, and Series streams */
@Path("/api/streams")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class StreamsController extends BaseController {

  /**
   * Get streams with filters GET
   * /api/streams?source_id=&type=&page=1&limit=20&category_id=&search=&stream_id=
   */
  @GET
  public Response getStreams(
      @QueryParam("source_id") Long sourceId,
      @QueryParam("type") String type,
      @QueryParam("category_id") Integer categoryId,
      @QueryParam("search") String search,
      @QueryParam("stream_id") Integer streamId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      return ResponseUtils.badRequest("source_id is required");
    }

    try {
      // TODO: Implement stream filtering across Live, VOD, Series
      return ResponseUtils.okWithPagination(
          java.util.Collections.emptyList(), PaginationMeta.of(page, limit, 0));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch streams: " + ex.getMessage());
    }
  }

  /** Get stream by ID GET /api/streams/:id?type= */
  @GET
  @Path("/{id}")
  public Response getStream(@PathParam("id") Long id, @QueryParam("type") String type) {
    try {
      // TODO: Implement stream retrieval by type
      return ResponseUtils.notFound("Stream not found");
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
      java.util.Map<String, String> request) {
    try {
      // TODO: Implement stream allow-deny update
      return ResponseUtils.okMessage("Stream updated");
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update stream: " + ex.getMessage());
    }
  }
}
