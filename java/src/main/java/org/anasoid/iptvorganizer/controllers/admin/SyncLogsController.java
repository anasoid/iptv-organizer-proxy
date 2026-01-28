package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

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
      return ResponseUtils.badRequest("Page and limit must be greater than 0");
    }

    if (sourceId == null) {
      return ResponseUtils.badRequest("source_id is required");
    }

    try {
      var logs = syncLogService.findBySourceId(sourceId);
      if (logs.size() > limit) {
        logs = logs.subList(0, limit);
      }
      long total = syncLogService.count();
      return ResponseUtils.okWithPagination(logs, PaginationMeta.of(page, limit, total));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch sync logs: " + ex.getMessage());
    }
  }

  /** Get sync log by ID GET /api/sync-logs/:id */
  @GET
  @Path("/{id}")
  public Response getSyncLog(@PathParam("id") Long id) {
    try {
      var log = syncLogService.getById(id);
      if (log != null) {
        return ResponseUtils.ok(log);
      } else {
        return ResponseUtils.notFound("Sync log not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.notFound("Sync log not found");
    }
  }

  /** Delete sync log DELETE /api/sync-logs/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteSyncLog(@PathParam("id") Long id) {
    try {
      syncLogService.delete(id);
      return ResponseUtils.okMessage("Sync log deleted successfully");
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to delete sync log: " + ex.getMessage());
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
    return ResponseUtils.ok(stats);
  }
}
