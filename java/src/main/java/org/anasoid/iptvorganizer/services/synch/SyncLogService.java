package org.anasoid.iptvorganizer.services.synch;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class SyncLogService extends BaseService<SyncLog, SyncLogRepository> {

  private static final Logger LOGGER = Logger.getLogger(SyncLogService.class.getName());
  @Inject SyncLogRepository repository;

  @Override
  protected SyncLogRepository getRepository() {
    return repository;
  }

  @Override
  public Uni<Long> create(SyncLog syncLog) {
    if (syncLog.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (syncLog.getSyncType() == null || syncLog.getSyncType().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Sync type is required"));
    }
    if (syncLog.getStatus() == null) {
      syncLog.setStatus(SyncLogStatus.RUNNING);
    }
    return repository.insert(syncLog);
  }

  /** Find sync logs by source ID */
  public Multi<SyncLog> findBySourceId(Long sourceId) {
    return repository.findBySourceId(sourceId);
  }

  public void fixInterruptedSyncs() {
    LOGGER.info("Checking for interrupted syncs from previous shutdown...");

    repository
        .findByStatus(SyncLogStatus.RUNNING)
        .collect()
        .asList()
        .subscribe()
        .with(
            runningLogs -> {
              if (!runningLogs.isEmpty()) {
                LOGGER.warning(
                    "Found "
                        + runningLogs.size()
                        + " syncs interrupted by shutdown, marking as interrupted");

                for (SyncLog log : runningLogs) {
                  log.setStatus(SyncLogStatus.INTERRUPTED);
                  log.setCompletedAt(LocalDateTime.now());
                  log.setErrorMessage("Application restarted during sync");

                  repository
                      .update(log)
                      .subscribe()
                      .with(
                          v -> LOGGER.info("Marked sync log " + log.getId() + " as interrupted"),
                          failure ->
                              LOGGER.severe(
                                  "Failed to mark sync log "
                                      + log.getId()
                                      + " as interrupted: "
                                      + failure.getMessage()));
                }
              } else {
                LOGGER.info("No interrupted syncs found");
              }
            },
            failure ->
                LOGGER.severe("Failed to check for interrupted syncs: " + failure.getMessage()));
  }
}
