package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.SyncLogDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;

/** Sync Logs controller */
@Path("/api/sync-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SyncLogsController extends BaseController {

  @Inject SyncLogService syncLogService;

  /** Get sync logs with filters GET /api/sync-logs?page=1&limit=20&source_id=&sync_type=&status= */
  @GET
  public Response getSyncLogs(
      @QueryParam("source_id") Long sourceId,
      @QueryParam("sync_type") String syncType,
      @QueryParam("status") String status,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      return Response.ok(ApiResponse.error("Page and limit must be greater than 0")).build();
    }

    if (sourceId == null) {
      return Response.ok(ApiResponse.error("source_id is required")).build();
    }

    try {
      var logs =
          syncLogService.findBySourceId(sourceId).stream()
              .map(SyncLogDTO::fromEntity)
              .limit(limit)
              .collect(Collectors.toList());
      long total = syncLogService.count();
      return Response.ok(
              ApiResponse.successWithPagination(logs, PaginationMeta.of(page, limit, total)))
          .build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to fetch sync logs: " + ex.getMessage()))
          .build();
    }
  }

  /** Get sync log by ID GET /api/sync-logs/:id */
  @GET
  @Path("/{id}")
  public Response getSyncLog(@PathParam("id") Long id) {
    try {
      var log = syncLogService.getById(id);
      if (log != null) {
        return Response.ok(ApiResponse.success(SyncLogDTO.fromEntity(log))).build();
      } else {
        return Response.ok(ApiResponse.error("Sync log not found")).build();
      }
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Sync log not found")).build();
    }
  }

  /** Delete sync log DELETE /api/sync-logs/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteSyncLog(@PathParam("id") Long id) {
    try {
      syncLogService.delete(id);
      return Response.ok(ApiResponse.success("Sync log deleted successfully")).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to delete sync log: " + ex.getMessage()))
          .build();
    }
  }

  /** Get sync logs statistics GET /api/sync-logs/stats?source_id=&sync_type= */
  @GET
  @Path("/stats")
  public Response getSyncStats(
      @QueryParam("source_id") Long sourceId, @QueryParam("sync_type") String syncType) {
    // TODO: Implement statistics retrieval
    var stats = new java.util.HashMap<String, Object>();
    stats.put("totalSyncs", 0);
    stats.put("successfulSyncs", 0);
    stats.put("failedSyncs", 0);
    return Response.ok(ApiResponse.success(stats)).build();
  }
}
