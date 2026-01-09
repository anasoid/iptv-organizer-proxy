package org.anasoid.iptvorganizer.services;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.SyncLog;
import org.anasoid.iptvorganizer.models.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.SyncLogRepository;

@ApplicationScoped
public class SyncLogService extends BaseService<SyncLog, SyncLogRepository> {

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
}
