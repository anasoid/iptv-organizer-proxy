package org.anasoid.iptvorganizer.services;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.SyncLog;
import org.anasoid.iptvorganizer.models.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;

/**
 * Handles cleanup of interrupted syncs on application startup Marks any sync_logs with status =
 * "running" as "interrupted"
 */
@ApplicationScoped
public class StartupService {

  private static final Logger LOGGER = Logger.getLogger(StartupService.class.getName());

  @Inject SyncLogRepository syncLogRepository;

  void onStart(@Observes StartupEvent event) {
    LOGGER.info("Checking for interrupted syncs from previous shutdown...");

    syncLogRepository
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

                  syncLogRepository
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
