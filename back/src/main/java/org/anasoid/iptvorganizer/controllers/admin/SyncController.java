package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.anasoid.iptvorganizer.services.SourceService;
import org.anasoid.iptvorganizer.services.synch.SyncLockManager;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Sync controller for active and status operations */
@Path("/api/sync")
@Produces(MediaType.APPLICATION_JSON)
public class SyncController extends BaseController {

  @Inject SourceService sourceService;

  @Inject SyncLockManager syncLockManager;

  /** Get sync status for all sources GET /api/sync/status */
  @GET
  @Path("/status")
  public Response getSyncStatus() {
    var statusList =
        sourceService.getAll().stream()
            .map(
                source -> {
                  var status = new HashMap<String, Object>();
                  status.put("sourceId", source.getId());
                  status.put("name", source.getName());

                  boolean isCurrentlySyncing = syncLockManager.isLocked(source.getId());
                  status.put("isSyncing", isCurrentlySyncing);

                  if (isCurrentlySyncing) {
                    syncLockManager
                        .getSyncMetadata(source.getId())
                        .ifPresent(
                            metadata -> {
                              status.put("currentSyncType", metadata.getSyncType());
                              status.put("syncStartTime", metadata.getStartTime());
                            });
                  }

                  status.put("lastSync", source.getLastSync());
                  status.put("nextSync", source.getNextSync());
                  return status;
                })
            .toList();
    return ResponseUtils.ok(statusList);
  }

  /** Get currently active sync operations GET /api/sync/active */
  @GET
  @Path("/active")
  public Response getActiveSyncs() {
    var response =
        syncLockManager.getActiveSyncs().stream()
            .map(
                metadata -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("sourceId", metadata.getSourceId());
                  map.put("syncType", metadata.getSyncType());
                  map.put("threadName", metadata.getThreadName());
                  map.put("threadId", metadata.getThreadId());
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
