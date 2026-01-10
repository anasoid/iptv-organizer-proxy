package org.anasoid.iptvorganizer.services;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.SyncSchedule;
import org.anasoid.iptvorganizer.repositories.SyncScheduleRepository;

@ApplicationScoped
public class SyncScheduleService extends BaseService<SyncSchedule, SyncScheduleRepository> {

  @Inject SyncScheduleRepository repository;

  @Override
  protected SyncScheduleRepository getRepository() {
    return repository;
  }

  @Override
  public Uni<Long> create(SyncSchedule schedule) {
    if (schedule.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (schedule.getTaskType() == null || schedule.getTaskType().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Task type is required"));
    }
    if (schedule.getNextSync() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Next sync time is required"));
    }
    return repository.insert(schedule);
  }
}
