package org.anasoid.iptvorganizer;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.migrations.SimpleMigrator;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.services.synch.SyncManager;

@ApplicationScoped
@Slf4j
@Startup
public class IpTvOrganizerApplication {

  @Inject SimpleMigrator simpleMigrator;
  @Inject SyncManager syncManager;
  @Inject SyncLogService syncLogService;

  /**
   * Scheduled task to check for sources needing sync Runs every 5 minutes by default, configurable
   * via sync.check.interval
   */
  @Scheduled(every = "{sync.check.interval}", identity = "sync-daemon")
  public void scheduledSync() {
    syncManager.scheduledSync();
  }

  void onStart(@Observes StartupEvent event) {
    log.info("On startup IpTvOrganizerApplication");
    simpleMigrator.startMigrations();
    syncLogService.fixInterruptedSyncs();
    log.info("On startup IpTvOrganizerApplication done");
  }
}
