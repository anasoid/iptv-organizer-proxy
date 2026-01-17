package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
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
    LOGGER.info("Checking for interrupted syncs from previous shutdown...");

    try {
      List<SyncLog> runningLogs = repository.findByStatus(SyncLogStatus.RUNNING);

      if (!runningLogs.isEmpty()) {
        LOGGER.warning(
            "Found "
                + runningLogs.size()
                + " syncs interrupted by shutdown, marking as interrupted");

        for (SyncLog log : runningLogs) {
          try {
            log.setStatus(SyncLogStatus.INTERRUPTED);
            log.setCompletedAt(LocalDateTime.now());
            log.setErrorMessage("Application restarted during sync");

            repository.update(log);
            LOGGER.info("Marked sync log " + log.getId() + " as interrupted");
          } catch (Exception e) {
            LOGGER.severe(
                "Failed to mark sync log " + log.getId() + " as interrupted: " + e.getMessage());
          }
        }
      } else {
        LOGGER.info("No interrupted syncs found");
      }
    } catch (Exception e) {
      LOGGER.severe("Failed to check for interrupted syncs: " + e.getMessage());
    }
  }
}
