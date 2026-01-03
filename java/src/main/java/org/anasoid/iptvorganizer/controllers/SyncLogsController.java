package org.anasoid.iptvorganizer.controllers;

import org.anasoid.iptvorganizer.dto.SyncLogDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.services.SyncLogService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Sync Logs controller
 */
@Path("/api/sync-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SyncLogsController extends BaseController {

    @Inject
    SyncLogService syncLogService;

    /**
     * Get sync logs with filters
     * GET /api/sync-logs?page=1&limit=20&source_id=&sync_type=&status=
     */
    @GET
    public Uni<?> getSyncLogs(
            @QueryParam("source_id") Long sourceId,
            @QueryParam("sync_type") String syncType,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("20") int limit) {

        if (page < 1 || limit < 1) {
            return Uni.createFrom().item(ApiResponse.error("Page and limit must be greater than 0"));
        }

        if (sourceId != null) {
            return Uni.combine().all().unis(
                syncLogService.findBySourceId(sourceId).map(SyncLogDTO::fromEntity).collect().asList(),
                syncLogService.count()
            ).asTuple()
                .map(tuple -> ApiResponse.successWithPagination(tuple.getItem1(), PaginationMeta.of(page, limit, tuple.getItem2())))
                .onFailure().recoverWithItem(ex -> ApiResponse.error("Failed to fetch sync logs: " + ex.getMessage()));
        } else {
            return Uni.createFrom().item(ApiResponse.error("source_id is required"));
        }
    }

    /**
     * Get sync log by ID
     * GET /api/sync-logs/:id
     */
    @GET
    @Path("/{id}")
    public Uni<?> getSyncLog(@PathParam("id") Long id) {
        return syncLogService.getById(id)
            .map(log -> log != null ? ApiResponse.success(SyncLogDTO.fromEntity(log)) : ApiResponse.error("Sync log not found"))
            .onFailure().recoverWithItem(ex -> ApiResponse.error("Sync log not found"));
    }

    /**
     * Delete sync log
     * DELETE /api/sync-logs/:id
     */
    @DELETE
    @Path("/{id}")
    public Uni<?> deleteSyncLog(@PathParam("id") Long id) {
        return syncLogService.delete(id)
            .map(v -> ApiResponse.success("Sync log deleted successfully"))
            .onFailure().recoverWithItem(ex -> ApiResponse.error("Failed to delete sync log: " + ex.getMessage()));
    }

    /**
     * Get sync logs statistics
     * GET /api/sync-logs/stats?source_id=&sync_type=
     */
    @GET
    @Path("/stats")
    public Uni<?> getSyncStats(
            @QueryParam("source_id") Long sourceId,
            @QueryParam("sync_type") String syncType) {
        // TODO: Implement statistics retrieval
        var stats = new java.util.HashMap<String, Object>();
        stats.put("totalSyncs", 0);
        stats.put("successfulSyncs", 0);
        stats.put("failedSyncs", 0);
        return Uni.createFrom().item(ApiResponse.success(stats));
    }
}
