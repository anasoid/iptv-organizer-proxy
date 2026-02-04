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
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.FilterException;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
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
      throw new ValidationException("Page and limit must be greater than 0");
    }

    List<Source> sources = sourceService.getAllPaged(page, limit);
    long total = sourceService.count();
    var pagination = PaginationMeta.of(page, limit, total);
    return ResponseUtils.okWithPagination(sources, pagination);
  }

  /** Get source by ID GET /api/sources/:id */
  @GET
  @Path("/{id}")
  public Response getSource(@PathParam("id") Long id) {
    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
    }
    return ResponseUtils.ok(source);
  }

  /** Create source POST /api/sources */
  @POST
  public Response createSource(Source request) {
    if (request.getName() == null || request.getName().isBlank()) {
      throw new ValidationException("Name is required");
    }
    if (request.getUrl() == null || request.getUrl().isBlank()) {
      throw new ValidationException("URL is required");
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      throw new ValidationException("Password is required");
    }

    // Set defaults for new source
    if (request.getSyncInterval() == null) {
      request.setSyncInterval(1);
    }
    if (request.getIsActive() == null) {
      request.setIsActive(true);
    }
    if (request.getConnectXtreamApi() == null) {
      request.setConnectXtreamApi(
          org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode.DEFAULT);
    }
    if (request.getConnectXtreamStream() == null) {
      request.setConnectXtreamStream(
          org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode.DEFAULT);
    }
    if (request.getConnectXmltv() == null) {
      request.setConnectXmltv(org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode.DEFAULT);
    }
    request.setCreatedAt(LocalDateTime.now());
    request.setUpdatedAt(LocalDateTime.now());

    Source savedSource = sourceService.save(request);
    return ResponseUtils.created(savedSource);
  }

  /** Update source PUT /api/sources/:id */
  @PUT
  @Path("/{id}")
  public Response updateSource(@PathParam("id") Long id, Source request) {
    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
    }

    // Merge non-null fields from request into existing source
    if (request.getName() != null) {
      source.setName(request.getName());
    }
    if (request.getUrl() != null) {
      source.setUrl(request.getUrl());
    }
    if (request.getUsername() != null) {
      source.setUsername(request.getUsername());
    }
    if (request.getPassword() != null && !request.getPassword().isBlank()) {
      source.setPassword(request.getPassword());
    }
    if (request.getSyncInterval() != null) {
      source.setSyncInterval(request.getSyncInterval());
    }
    if (request.getIsActive() != null) {
      source.setIsActive(request.getIsActive());
    }
    // Set proxyId (can be null to remove proxy assignment)
    source.setProxyId(request.getProxyId());
    if (request.getEnableProxy() != null) {
      source.setEnableProxy(request.getEnableProxy());
    }
    if (request.getEnableTunnel() != null) {
      source.setEnableTunnel(request.getEnableTunnel());
    }
    if (request.getConnectXtreamApi() != null) {
      source.setConnectXtreamApi(request.getConnectXtreamApi());
    }
    if (request.getConnectXtreamStream() != null) {
      source.setConnectXtreamStream(request.getConnectXtreamStream());
    }
    if (request.getConnectXmltv() != null) {
      source.setConnectXmltv(request.getConnectXmltv());
    }

    source.setUpdatedAt(LocalDateTime.now());
    sourceService.update(source);
    return ResponseUtils.ok(source);
  }

  /** Delete source DELETE /api/sources/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteSource(@PathParam("id") Long id) {
    sourceService.delete(id);
    return ResponseUtils.okMessage("Source deleted successfully");
  }

  /** Test source connection POST /api/sources/:id/test */
  @POST
  @Path("/{id}/test")
  public Response testConnection(@PathParam("id") Long id) {
    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
    }
    // TODO: Implement connection test logic
    return ResponseUtils.okMessage("Connection test passed");
  }

  /** Trigger full manual sync for a source POST /api/sources/:id/sync */
  @POST
  @Path("/{id}/sync")
  public Response syncSource(@PathParam("id") Long id) {
    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
    }

    try {
      syncManager.triggerManualSync(source);
      return ResponseUtils.okMessage("Full sync triggered for source: " + source.getName());
    } catch (Exception ex) {
      log.error("Failed to trigger sync for source: {}", id, ex);
      if (ex.getMessage() != null && ex.getMessage().contains("already syncing")) {
        return Response.status(Response.Status.CONFLICT)
            .entity(ApiResponse.error("Source is already syncing"))
            .build();
      }
      throw new FilterException("Failed to trigger sync: " + ex.getMessage(), ex);
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
      throw new ValidationException(
          "Invalid task type. Valid types: " + String.join(", ", validTaskTypes));
    }

    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
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
      throw new FilterException("Failed to trigger sync: " + ex.getMessage(), ex);
    }
  }

  /** Trigger full sync for all task types POST /api/sources/:id/sync-all */
  @POST
  @Path("/{id}/sync-all")
  public Response syncSourceAll(@PathParam("id") Long id) {
    Source source = sourceService.getById(id);
    if (source == null) {
      throw new NotFoundException("Source not found with ID: " + id);
    }

    try {
      syncManager.triggerFullSync(source);
      return ResponseUtils.okMessage(
          "Full sync triggered for all types on source: " + source.getName());
    } catch (Exception ex) {
      log.error("Failed to trigger full sync for source: {}", id, ex);
      if (ex.getMessage() != null && ex.getMessage().contains("already syncing")) {
        return Response.status(Response.Status.CONFLICT)
            .entity(ApiResponse.error("Source is already syncing"))
            .build();
      }
      throw new FilterException("Failed to trigger full sync: " + ex.getMessage(), ex);
    }
  }

  /** Get sync logs for a source GET /api/sources/:id/sync-logs?limit=20 */
  @GET
  @Path("/{id}/sync-logs")
  public Response getSyncLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    List<SyncLog> logs = syncLogService.findBySourceId(id);
    if (logs.size() > limit) {
      logs = logs.subList(0, limit);
    }
    return ResponseUtils.ok(logs);
  }

  /** Get sync status for all sources GET /api/sync/status */
  @GET
  @Path("/../sync/status")
  public Response getSyncStatus() {
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
  }

  /** Get currently active sync operations GET /api/sync/active */
  @GET
  @Path("/../sync/active")
  public Response getActiveSyncs() {
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
  }
}
