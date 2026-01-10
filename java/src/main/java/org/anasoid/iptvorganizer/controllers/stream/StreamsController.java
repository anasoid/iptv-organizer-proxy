package org.anasoid.iptvorganizer.controllers.stream;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.anasoid.iptvorganizer.controllers.BaseController;
import org.anasoid.iptvorganizer.dto.StreamDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;

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
  public Uni<?> getStreams(
      @QueryParam("source_id") Long sourceId,
      @QueryParam("type") String type,
      @QueryParam("category_id") Integer categoryId,
      @QueryParam("search") String search,
      @QueryParam("stream_id") Integer streamId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      return Uni.createFrom().item(ApiResponse.error("source_id is required"));
    }

    // TODO: Implement stream filtering across Live, VOD, Series
    return Uni.createFrom()
        .item(
            ApiResponse.successWithPagination(
                java.util.Collections.emptyList(), PaginationMeta.of(page, limit, 0)));
  }

  /** Get stream by ID GET /api/streams/:id?type= */
  @GET
  @Path("/{id}")
  public Uni<?> getStream(@PathParam("id") Long id, @QueryParam("type") String type) {
    // TODO: Implement stream retrieval by type
    return Uni.createFrom().item(ApiResponse.success(new StreamDTO()));
  }

  /** Update stream allow-deny status PATCH /api/streams/:id/allow-deny?type= */
  @PATCH
  @Path("/{id}/allow-deny")
  public Uni<?> updateStreamAllowDeny(
      @PathParam("id") Long id,
      @QueryParam("type") String type,
      java.util.Map<String, String> request) {
    // TODO: Implement stream allow-deny update
    return Uni.createFrom().item(ApiResponse.success("Stream updated"));
  }
}
