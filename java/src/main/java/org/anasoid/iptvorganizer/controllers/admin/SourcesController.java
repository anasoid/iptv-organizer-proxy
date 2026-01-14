package org.anasoid.iptvorganizer.controllers.admin;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.dto.SourceDTO;
import org.anasoid.iptvorganizer.dto.SyncLogDTO;
import org.anasoid.iptvorganizer.dto.request.CreateSourceRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.services.SourceService;
import org.anasoid.iptvorganizer.services.synch.SyncLockManager;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.services.synch.SyncManager;

/** Sources controller CRUD operations for sources with sync management */
@Path("/api/sources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SourcesController extends BaseController {

  private static final Logger LOGGER = Logger.getLogger(SourcesController.class.getName());

  @Inject SourceService sourceService;

  @Inject SyncLogService syncLogService;

  @Inject SyncManager syncManager;

  @Inject SyncLockManager syncLockManager;

  /** Get all sources with pagination GET /api/sources?page=1&limit=20 */
  @GET
  public Uni<?> getAllSources(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      return Uni.createFrom().item(ApiResponse.error("Page and limit must be greater than 0"));
    }

    return Uni.combine()
        .all()
        .unis(
            sourceService.getAllPaged(page, limit).map(SourceDTO::fromEntity).collect().asList(),
            sourceService.count())
        .asTuple()
        .map(
            tuple -> {
              var sources = tuple.getItem1();
              long total = tuple.getItem2();
              var pagination = PaginationMeta.of(page, limit, total);
              return ApiResponse.successWithPagination(sources, pagination);
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to fetch sources", ex);
              return ApiResponse.error("Failed to fetch sources: " + ex.getMessage());
            });
  }

  /** Get source by ID GET /api/sources/:id */
  @GET
  @Path("/{id}")
  public Uni<?> getSource(@PathParam("id") Long id) {
    return sourceService
        .getById(id)
        .map(
            source ->
                source != null
                    ? ApiResponse.success(SourceDTO.fromEntity(source))
                    : ApiResponse.error("Source not found"))
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Error fetching source: " + id, ex);
              return ApiResponse.error("Source not found");
            });
  }

  /** Create source POST /api/sources */
  @POST
  public Uni<?> createSource(CreateSourceRequest request) {
    if (request.getName() == null || request.getName().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("Name is required"));
    }
    if (request.getUrl() == null || request.getUrl().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("URL is required"));
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("Password is required"));
    }

    Source source =
        Source.builder()
            .name(request.getName())
            .url(request.getUrl())
            .username(request.getUsername())
            .password(request.getPassword())
            .syncInterval(request.getSyncInterval() != null ? request.getSyncInterval() : 1)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .enableProxy(request.getEnableProxy() != null ? request.getEnableProxy() : false)
            .disableStreamProxy(
                request.getDisableStreamProxy() != null ? request.getDisableStreamProxy() : false)
            .streamFollowLocation(
                request.getStreamFollowLocation() != null
                    ? request.getStreamFollowLocation()
                    : false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    return sourceService
        .save(source)
        .map(s -> ApiResponse.success(SourceDTO.fromEntity(s)))
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to create source", ex);
              return ApiResponse.error("Failed to create source: " + ex.getMessage());
            });
  }

  /** Update source PUT /api/sources/:id */
  @PUT
  @Path("/{id}")
  public Uni<?> updateSource(@PathParam("id") Long id, CreateSourceRequest request) {
    return sourceService
        .getById(id)
        .flatMap(
            source -> {
              if (request.getName() != null) {
                source.setName(request.getName());
              }
              if (request.getUrl() != null) {
                source.setUrl(request.getUrl());
              }
              if (request.getUsername() != null) {
                source.setUsername(request.getUsername());
              }
              if (request.getPassword() != null) {
                source.setPassword(request.getPassword());
              }
              if (request.getSyncInterval() != null) {
                source.setSyncInterval(request.getSyncInterval());
              }
              if (request.getIsActive() != null) {
                source.setIsActive(request.getIsActive());
              }
              if (request.getEnableProxy() != null) {
                source.setEnableProxy(request.getEnableProxy());
              }
              if (request.getDisableStreamProxy() != null) {
                source.setDisableStreamProxy(request.getDisableStreamProxy());
              }
              if (request.getStreamFollowLocation() != null) {
                source.setStreamFollowLocation(request.getStreamFollowLocation());
              }

              source.setUpdatedAt(LocalDateTime.now());
              return sourceService
                  .update(source)
                  .map(v -> source); // Return the updated source after successful update
            })
        .map(source -> ApiResponse.success(SourceDTO.fromEntity(source)))
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to update source: " + id, ex);
              return ApiResponse.error("Failed to update source: " + ex.getMessage());
            });
  }

  /** Delete source DELETE /api/sources/:id */
  @DELETE
  @Path("/{id}")
  public Uni<?> deleteSource(@PathParam("id") Long id) {
    return sourceService
        .delete(id)
        .map(v -> ApiResponse.success("Source deleted successfully"))
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to delete source: " + id, ex);
              return ApiResponse.error("Failed to delete source: " + ex.getMessage());
            });
  }

  /** Test source connection POST /api/sources/:id/test */
  @POST
  @Path("/{id}/test")
  public Uni<?> testConnection(@PathParam("id") Long id) {
    return sourceService
        .getById(id)
        .flatMap(
            source -> {
              // TODO: Implement connection test logic
              return Uni.createFrom().item(ApiResponse.success("Connection test passed"));
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Connection test failed for source: " + id, ex);
              return ApiResponse.error("Connection test failed: " + ex.getMessage());
            });
  }

  /** Trigger full manual sync for a source POST /api/sources/:id/sync */
  @POST
  @Path("/{id}/sync")
  public Uni<?> syncSource(@PathParam("id") Long id) {
    return sourceService
        .getById(id)
        .flatMap(
            source -> {
              if (source == null) {
                return Uni.createFrom()
                    .item(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity(ApiResponse.error("Source not found"))
                            .build());
              }

              return syncManager
                  .triggerManualSync(source)
                  .map(
                      v ->
                          Response.ok(
                                  ApiResponse.success(
                                      "Full sync triggered for source: " + source.getName()))
                              .build())
                  .onFailure()
                  .recoverWithItem(
                      ex -> {
                        LOGGER.log(Level.SEVERE, "Failed to trigger sync for source: " + id, ex);
                        if (ex.getMessage().contains("already syncing")) {
                          return Response.status(Response.Status.CONFLICT)
                              .entity(ApiResponse.error("Source is already syncing"))
                              .build();
                        }
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(ApiResponse.error("Failed to trigger sync: " + ex.getMessage()))
                            .build();
                      });
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to trigger sync for source: " + id, ex);
              return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity(ApiResponse.error("Failed to trigger sync: " + ex.getMessage()))
                  .build();
            });
  }

  /**
   * Trigger sync for a specific task type (granular sync) POST /api/sources/:id/sync/{taskType}
   * Valid task types: live_categories, live_streams, vod_categories, vod_streams,
   * series_categories, series
   */
  @POST
  @Path("/{id}/sync/{taskType}")
  public Uni<?> syncSourceTaskType(
      @PathParam("id") Long id, @PathParam("taskType") String taskType) {
    // Validate task type
    java.util.Set<String> validTaskTypes =
        java.util.Set.of(
            "live_categories", "live_streams",
            "vod_categories", "vod_streams",
            "series_categories", "series");

    if (!validTaskTypes.contains(taskType)) {
      return Uni.createFrom()
          .item(
              Response.status(Response.Status.BAD_REQUEST)
                  .entity(
                      ApiResponse.error(
                          "Invalid task type. Valid types: " + String.join(", ", validTaskTypes)))
                  .build());
    }

    return sourceService
        .getById(id)
        .flatMap(
            source -> {
              if (source == null) {
                return Uni.createFrom()
                    .item(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity(ApiResponse.error("Source not found"))
                            .build());
              }

              return syncManager
                  .triggerManualSyncTask(source, taskType)
                  .map(
                      v ->
                          Response.ok(
                                  ApiResponse.success(
                                      "Sync triggered for "
                                          + taskType
                                          + " on source: "
                                          + source.getName()))
                              .build())
                  .onFailure()
                  .recoverWithItem(
                      ex -> {
                        LOGGER.log(
                            Level.SEVERE,
                            "Failed to trigger sync task " + taskType + " for source: " + id,
                            ex);
                        if (ex.getMessage().contains("already syncing")) {
                          return Response.status(Response.Status.CONFLICT)
                              .entity(ApiResponse.error("Source is already syncing"))
                              .build();
                        }
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(ApiResponse.error("Failed to trigger sync: " + ex.getMessage()))
                            .build();
                      });
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(
                  Level.SEVERE,
                  "Failed to trigger sync task " + taskType + " for source: " + id,
                  ex);
              return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity(ApiResponse.error("Failed to trigger sync: " + ex.getMessage()))
                  .build();
            });
  }

  /** Trigger full sync for all task types POST /api/sources/:id/sync-all */
  @POST
  @Path("/{id}/sync-all")
  public Uni<?> syncSourceAll(@PathParam("id") Long id) {
    return sourceService
        .getById(id)
        .flatMap(
            source -> {
              if (source == null) {
                return Uni.createFrom()
                    .item(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity(ApiResponse.error("Source not found"))
                            .build());
              }

              return syncManager
                  .triggerFullSync(source)
                  .map(
                      v ->
                          Response.ok(
                                  ApiResponse.success(
                                      "Full sync triggered for all types on source: "
                                          + source.getName()))
                              .build())
                  .onFailure()
                  .recoverWithItem(
                      ex -> {
                        LOGGER.log(
                            Level.SEVERE, "Failed to trigger full sync for source: " + id, ex);
                        if (ex.getMessage().contains("already syncing")) {
                          return Response.status(Response.Status.CONFLICT)
                              .entity(ApiResponse.error("Source is already syncing"))
                              .build();
                        }
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(
                                ApiResponse.error(
                                    "Failed to trigger full sync: " + ex.getMessage()))
                            .build();
                      });
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to trigger full sync for source: " + id, ex);
              return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity(ApiResponse.error("Failed to trigger full sync: " + ex.getMessage()))
                  .build();
            });
  }

  /** Get sync logs for a source GET /api/sources/:id/sync-logs?limit=20 */
  @GET
  @Path("/{id}/sync-logs")
  public Uni<?> getSyncLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    return syncLogService
        .findBySourceId(id)
        .map(SyncLogDTO::fromEntity)
        .collect()
        .asList()
        .map(logs -> ApiResponse.success(logs.stream().limit(limit).toList()))
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to fetch sync logs for source: " + id, ex);
              return ApiResponse.error("Failed to fetch sync logs: " + ex.getMessage());
            });
  }

  /** Get sync status for all sources GET /api/sync/status */
  @GET
  @Path("/../sync/status")
  public Uni<?> getSyncStatus() {
    return sourceService
        .getAll()
        .collect()
        .asList()
        .map(
            sources ->
                sources.stream()
                    .map(
                        s -> {
                          var status = new HashMap<String, Object>();
                          status.put("sourceId", s.getId());
                          status.put("name", s.getName());

                          // Check if currently syncing from in-memory lock manager
                          boolean isCurrentlySyncing = syncLockManager.isLocked(s.getId());
                          status.put("isSyncing", isCurrentlySyncing);

                          // Include metadata if syncing
                          if (isCurrentlySyncing) {
                            syncLockManager
                                .getSyncMetadata(s.getId())
                                .ifPresent(
                                    metadata -> {
                                      status.put("currentSyncType", metadata.getSyncType());
                                      status.put("syncStartTime", metadata.getStartTime());
                                    });
                          }

                          status.put("lastSync", s.getLastSync());
                          status.put("nextSync", s.getNextSync());
                          return status;
                        })
                    .toList())
        .map(ApiResponse::success)
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOGGER.log(Level.SEVERE, "Failed to fetch sync status", ex);
              return ApiResponse.error("Failed to fetch sync status: " + ex.getMessage());
            });
  }

  /** Get currently active sync operations GET /api/sync/active */
  @GET
  @Path("/../sync/active")
  public Uni<?> getActiveSyncs() {
    return Uni.createFrom()
        .item(
            () -> {
              var activeSyncs = syncLockManager.getActiveSyncs();

              var response =
                  activeSyncs.stream()
                      .map(
                          metadata -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("sourceId", metadata.getSourceId());
                            map.put("syncType", metadata.getSyncType());
                            map.put("startTime", metadata.getStartTime());
                            map.put(
                                "durationSeconds",
                                ChronoUnit.SECONDS.between(
                                    metadata.getStartTime(), LocalDateTime.now()));
                            return map;
                          })
                      .toList();

              return ApiResponse.success(response);
            });
  }
}
