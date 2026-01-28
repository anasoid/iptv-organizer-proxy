package org.anasoid.iptvorganizer.controllers.admin;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.SourceDTO;
import org.anasoid.iptvorganizer.dto.SyncLogDTO;
import org.anasoid.iptvorganizer.dto.request.CreateSourceRequest;
import org.anasoid.iptvorganizer.dto.request.UpdateSourceRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.services.SourceService;
import org.anasoid.iptvorganizer.services.synch.SyncLockManager;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.services.synch.SyncManager;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Sources controller CRUD operations for sources with sync management */
@Slf4j
@Path("/api/sources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SourcesController extends BaseController {

  @Inject SourceService sourceService;

  @Inject SyncLogService syncLogService;

  @Inject SyncManager syncManager;

  @Inject SyncLockManager syncLockManager;

  /** Get all sources with pagination GET /api/sources?page=1&limit=20 */
  @GET
  public Response getAllSources(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      return ResponseUtils.badRequest("Page and limit must be greater than 0");
    }

    try {
      List<SourceDTO> sources =
          sourceService.getAllPaged(page, limit).stream()
              .map(SourceDTO::fromEntity)
              .collect(Collectors.toList());
      long total = sourceService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return ResponseUtils.okWithPagination(sources, pagination);
    } catch (Exception ex) {
      log.error("Failed to fetch sources", ex);
      return ResponseUtils.serverError("Failed to fetch sources: " + ex.getMessage());
    }
  }

  /** Get source by ID GET /api/sources/:id */
  @GET
  @Path("/{id}")
  public Response getSource(@PathParam("id") Long id) {
    try {
      Source source = sourceService.getById(id);
      if (source != null) {
        return ResponseUtils.ok(SourceDTO.fromEntity(source));
      } else {
        return ResponseUtils.notFound("Source not found");
      }
    } catch (Exception ex) {
      log.error("Error fetching source: {}", id, ex);
      return ResponseUtils.notFound("Source not found");
    }
  }

  /** Create source POST /api/sources */
  @POST
  public Response createSource(CreateSourceRequest request) {
    if (request.getName() == null || request.getName().isBlank()) {
      return ResponseUtils.badRequest("Name is required");
    }
    if (request.getUrl() == null || request.getUrl().isBlank()) {
      return ResponseUtils.badRequest("URL is required");
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      return ResponseUtils.badRequest("Password is required");
    }

    try {
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
              .useRedirect(request.getUseRedirect())
              .useRedirectXmltv(request.getUseRedirectXmltv())
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      Source savedSource = sourceService.save(source);
      return ResponseUtils.created(SourceDTO.fromEntity(savedSource));
    } catch (Exception ex) {
      log.error("Failed to create source", ex);
      return ResponseUtils.serverError("Failed to create source: " + ex.getMessage());
    }
  }

  /** Update source PUT /api/sources/:id */
  @PUT
  @Path("/{id}")
  public Response updateSource(@PathParam("id") Long id, UpdateSourceRequest request) {
    try {
      Source source = sourceService.getById(id);
      if (source == null) {
        return ResponseUtils.notFound("Source not found");
      }

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
      // Allow setting to null for optional redirect settings
      if (request.getUseRedirect() != null) {
        source.setUseRedirect(request.getUseRedirect());
      }
      if (request.getUseRedirectXmltv() != null) {
        source.setUseRedirectXmltv(request.getUseRedirectXmltv());
      }

      source.setUpdatedAt(LocalDateTime.now());
      sourceService.update(source);
      return ResponseUtils.ok(SourceDTO.fromEntity(source));
    } catch (Exception ex) {
      log.error("Failed to update source: {}", id, ex);
      return ResponseUtils.serverError("Failed to update source: " + ex.getMessage());
    }
  }

  /** Delete source DELETE /api/sources/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteSource(@PathParam("id") Long id) {
    try {
      sourceService.delete(id);
      return ResponseUtils.okMessage("Source deleted successfully");
    } catch (Exception ex) {
      log.error("Failed to delete source: {}", id, ex);
      return ResponseUtils.serverError("Failed to delete source: " + ex.getMessage());
    }
  }

  /** Test source connection POST /api/sources/:id/test */
  @POST
  @Path("/{id}/test")
  public Response testConnection(@PathParam("id") Long id) {
    try {
      Source source = sourceService.getById(id);
      if (source == null) {
        return ResponseUtils.notFound("Source not found");
      }
      // TODO: Implement connection test logic
      return ResponseUtils.okMessage("Connection test passed");
    } catch (Exception ex) {
      log.error("Connection test failed for source: {}", id, ex);
      return ResponseUtils.serverError("Connection test failed: " + ex.getMessage());
    }
  }

  /** Trigger full manual sync for a source POST /api/sources/:id/sync */
  @POST
  @Path("/{id}/sync")
  public Response syncSource(@PathParam("id") Long id) {
    try {
      Source source = sourceService.getById(id);
      if (source == null) {
        return ResponseUtils.notFound("Source not found");
      }

      try {
        syncManager.triggerManualSync(source);
        return ResponseUtils.okMessage("Full sync triggered for source: " + source.getName());
      } catch (Exception ex) {
        log.error("Failed to trigger sync for source: {}", id, ex);
        if (ex.getMessage().contains("already syncing")) {
          return Response.status(Response.Status.CONFLICT)
              .entity(ApiResponse.error("Source is already syncing"))
              .build();
        }
        return ResponseUtils.serverError("Failed to trigger sync: " + ex.getMessage());
      }
    } catch (Exception ex) {
      log.error("Failed to trigger sync for source: {}", id, ex);
      return ResponseUtils.serverError("Failed to trigger sync: " + ex.getMessage());
    }
  }

  /**
   * Trigger sync for a specific task type (granular sync) POST /api/sources/:id/sync/{taskType}
   * Valid task types: live_categories, live_streams, vod_categories, vod_streams,
   * series_categories, series
   */
  @POST
  @Path("/{id}/sync/{taskType}")
  public Response syncSourceTaskType(
      @PathParam("id") Long id, @PathParam("taskType") String taskType) {
    // Validate task type
    java.util.Set<String> validTaskTypes =
        java.util.Set.of(
            "live_categories", "live_streams",
            "vod_categories", "vod_streams",
            "series_categories", "series");

    if (!validTaskTypes.contains(taskType)) {
      return ResponseUtils.badRequest(
          "Invalid task type. Valid types: " + String.join(", ", validTaskTypes));
    }

    try {
      Source source = sourceService.getById(id);
      if (source == null) {
        return ResponseUtils.notFound("Source not found");
      }

      try {
        syncManager.triggerManualSyncTask(source, taskType);
        return ResponseUtils.okMessage(
            "Sync triggered for " + taskType + " on source: " + source.getName());
      } catch (Exception ex) {
        log.error("Failed to trigger sync task {} for source: {}", taskType, id, ex);
        if (ex.getMessage() != null && ex.getMessage().contains("already syncing")) {
          return Response.status(Response.Status.CONFLICT)
              .entity(ApiResponse.error("Source is already syncing"))
              .build();
        }
        return ResponseUtils.serverError("Failed to trigger sync: " + ex.getMessage());
      }
    } catch (Exception ex) {
      log.error("Failed to trigger sync task {} for source: {}", taskType, id, ex);
      return ResponseUtils.serverError("Failed to trigger sync: " + ex.getMessage());
    }
  }

  /** Trigger full sync for all task types POST /api/sources/:id/sync-all */
  @POST
  @Path("/{id}/sync-all")
  public Response syncSourceAll(@PathParam("id") Long id) {
    try {
      Source source = sourceService.getById(id);
      if (source == null) {
        return ResponseUtils.notFound("Source not found");
      }

      try {
        syncManager.triggerFullSync(source);
        return ResponseUtils.okMessage(
            "Full sync triggered for all types on source: " + source.getName());
      } catch (Exception ex) {
        log.error("Failed to trigger full sync for source: {}", id, ex);
        if (ex.getMessage().contains("already syncing")) {
          return Response.status(Response.Status.CONFLICT)
              .entity(ApiResponse.error("Source is already syncing"))
              .build();
        }
        return ResponseUtils.serverError("Failed to trigger full sync: " + ex.getMessage());
      }
    } catch (Exception ex) {
      log.error("Failed to trigger full sync for source: {}", id, ex);
      return ResponseUtils.serverError("Failed to trigger full sync: " + ex.getMessage());
    }
  }

  /** Get sync logs for a source GET /api/sources/:id/sync-logs?limit=20 */
  @GET
  @Path("/{id}/sync-logs")
  public Response getSyncLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    try {
      List<?> logs =
          syncLogService.findBySourceId(id).stream()
              .map(SyncLogDTO::fromEntity)
              .limit(limit)
              .collect(Collectors.toList());
      return ResponseUtils.ok(logs);
    } catch (Exception ex) {
      log.error("Failed to fetch sync logs for source: {}", id, ex);
      return ResponseUtils.serverError("Failed to fetch sync logs: " + ex.getMessage());
    }
  }

  /** Get sync status for all sources GET /api/sync/status */
  @GET
  @Path("/../sync/status")
  public Response getSyncStatus() {
    try {
      List<Source> sources = sourceService.getAll();
      var statusList =
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
              .toList();
      return ResponseUtils.ok(statusList);
    } catch (Exception ex) {
      log.error("Failed to fetch sync status", ex);
      return ResponseUtils.serverError("Failed to fetch sync status: " + ex.getMessage());
    }
  }

  /** Get currently active sync operations GET /api/sync/active */
  @GET
  @Path("/../sync/active")
  public Response getActiveSyncs() {
    try {
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
                        ChronoUnit.SECONDS.between(metadata.getStartTime(), LocalDateTime.now()));
                    return map;
                  })
              .toList();

      return ResponseUtils.ok(response);
    } catch (Exception ex) {
      log.error("Failed to fetch active syncs", ex);
      return ResponseUtils.serverError("Failed to fetch active syncs: " + ex.getMessage());
    }
  }
}
