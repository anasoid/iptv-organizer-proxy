package org.anasoid.iptvorganizer;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.java.Log;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.services.synch.SyncService;

@ApplicationScoped
@Log
@Startup
public class IpTvOrganizerApplication {

  @Inject SyncService syncService;
  @Inject SyncLogService syncLogService;

  /**
   * Scheduled task to check for sources needing sync Runs every 5 minutes by default, configurable
   * via sync.check.interval
   */
  @Scheduled(every = "{sync.check.interval}", identity = "sync-daemon")
  public void scheduledSync() {
    syncService.scheduledSync();
  }

  void onStart(@Observes StartupEvent event) {
    log.info("On startup IpTvOrganizerApplication");
    syncLogService.fixInterruptedSyncs();
    log.info("On startup IpTvOrganizerApplication done");
  }
}
