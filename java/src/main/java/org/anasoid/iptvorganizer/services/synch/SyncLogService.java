package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@Slf4j
@ApplicationScoped
public class SyncLogService extends BaseService<SyncLog, SyncLogRepository> {

  @Inject SyncLogRepository repository;

  @Override
  protected SyncLogRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(SyncLog syncLog) {
    if (syncLog.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (syncLog.getSyncType() == null || syncLog.getSyncType().isBlank()) {
      throw new IllegalArgumentException("Sync type is required");
    }
    if (syncLog.getStatus() == null) {
      syncLog.setStatus(SyncLogStatus.RUNNING);
    }
    return repository.insert(syncLog);
  }

  /** Find sync logs by source ID */
  public List<SyncLog> findBySourceId(Long sourceId) {
    return repository.findBySourceId(sourceId);
  }

  public void fixInterruptedSyncs() {
    log.info("Checking for interrupted syncs from previous shutdown...");

    try {
      List<SyncLog> runningLogs = repository.findByStatus(SyncLogStatus.RUNNING);

      if (!runningLogs.isEmpty()) {
        log.warn(
            "Found "
                + runningLogs.size()
                + " syncs interrupted by shutdown, marking as interrupted");

        for (SyncLog synclog : runningLogs) {
          try {
            synclog.setStatus(SyncLogStatus.INTERRUPTED);
            synclog.setCompletedAt(LocalDateTime.now());
            synclog.setErrorMessage("Application restarted during sync");

            repository.update(synclog);
            log.info("Marked sync log " + synclog.getId() + " as interrupted");
          } catch (Exception e) {
            log.error(
                "Failed to mark sync log "
                    + synclog.getId()
                    + " as interrupted: "
                    + e.getMessage());
          }
        }
      } else {
        log.info("No interrupted syncs found");
      }
    } catch (Exception e) {
      log.error("Failed to check for interrupted syncs: " + e.getMessage());
    }
  }
}
