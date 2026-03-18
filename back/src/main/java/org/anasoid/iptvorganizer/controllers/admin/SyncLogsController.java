package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Sync Logs controller */
@Path("/api/sync-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SyncLogsController extends BaseController {

  @Inject SyncLogService syncLogService;

  /** Get sync logs with filters GET /api/sync-logs?page=1&limit=20&sourceId=&syncType=&status= */
  @GET
  public Response getSyncLogs(
      @QueryParam("sourceId") Long sourceId,
      @QueryParam("syncType") String syncType,
      @QueryParam("status") String status,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      throw new ValidationException("Page and limit must be greater than 0");
    }

    java.util.List<org.anasoid.iptvorganizer.models.entity.SyncLog> logs;

    // Fetch logs by source if sourceId provided, otherwise fetch all logs
    if (sourceId != null) {
      logs = syncLogService.findBySourceId(sourceId);
    } else {
      logs = syncLogService.getAll();
    }

    // Apply status filter if provided
    if (status != null && !status.isBlank()) {
      final String statusFilter = status;
      logs =
          logs.stream()
              .filter(l -> l.getStatus().toString().equalsIgnoreCase(statusFilter))
              .collect(java.util.stream.Collectors.toList());
    }

    // Apply sync type filter if provided
    if (syncType != null && !syncType.isBlank()) {
      final String typeFilter = syncType;
      logs =
          logs.stream()
              .filter(l -> l.getSyncType().equalsIgnoreCase(typeFilter))
              .collect(java.util.stream.Collectors.toList());
    }

    // Apply pagination
    long total = logs.size();
    int startIdx = (page - 1) * limit;
    int endIdx = Math.min(startIdx + limit, (int) total);
    java.util.List<org.anasoid.iptvorganizer.models.entity.SyncLog> paginatedLogs =
        startIdx < logs.size() ? logs.subList(startIdx, endIdx) : java.util.Collections.emptyList();

    return ResponseUtils.okWithPagination(paginatedLogs, PaginationMeta.of(page, limit, total));
  }

  /** Get sync log by ID GET /api/sync-logs/:id */
  @GET
  @Path("/{id}")
  public Response getSyncLog(@PathParam("id") Long id) {
    var log = syncLogService.getById(id);
    if (log == null) {
      throw new NotFoundException("Sync log not found with ID: " + id);
    }
    return ResponseUtils.ok(log);
  }

  /** Delete sync log DELETE /api/sync-logs/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteSyncLog(@PathParam("id") Long id) {
    syncLogService.delete(id);
    return ResponseUtils.okMessage("Sync log deleted successfully");
  }

  /** Get sync logs statistics GET /api/sync-logs/stats?sourceId=&syncType= */
  @GET
  @Path("/stats")
  public Response getSyncStats(
      @QueryParam("sourceId") Long sourceId, @QueryParam("syncType") String syncType) {
    java.util.List<org.anasoid.iptvorganizer.models.entity.SyncLog> logs;

    // Fetch logs by source if sourceId provided, otherwise fetch all logs
    if (sourceId != null) {
      logs = syncLogService.findBySourceId(sourceId);
    } else {
      logs = syncLogService.getAll();
    }

    // Apply sync type filter if provided
    if (syncType != null && !syncType.isBlank()) {
      final String typeFilter = syncType;
      logs =
          logs.stream()
              .filter(l -> l.getSyncType().equalsIgnoreCase(typeFilter))
              .collect(java.util.stream.Collectors.toList());
    }

    // Calculate statistics
    long totalSyncs = logs.size();
    long completedSyncs =
        logs.stream().filter(l -> "COMPLETED".equalsIgnoreCase(l.getStatus().toString())).count();
    long failedSyncs =
        logs.stream().filter(l -> "FAILED".equalsIgnoreCase(l.getStatus().toString())).count();
    long runningSyncs =
        logs.stream().filter(l -> "RUNNING".equalsIgnoreCase(l.getStatus().toString())).count();
    long totalAdded =
        logs.stream().mapToLong(l -> l.getItemsAdded() != null ? l.getItemsAdded() : 0).sum();
    long totalUpdated =
        logs.stream().mapToLong(l -> l.getItemsUpdated() != null ? l.getItemsUpdated() : 0).sum();
    long totalDeleted =
        logs.stream().mapToLong(l -> l.getItemsDeleted() != null ? l.getItemsDeleted() : 0).sum();

    var stats = new java.util.HashMap<String, Object>();
    stats.put("totalSyncs", totalSyncs);
    stats.put("completedSyncs", completedSyncs);
    stats.put("failedSyncs", failedSyncs);
    stats.put("runningSyncs", runningSyncs);
    stats.put("totalAdded", totalAdded);
    stats.put("totalUpdated", totalUpdated);
    stats.put("totalDeleted", totalDeleted);

    return ResponseUtils.ok(stats);
  }
}
