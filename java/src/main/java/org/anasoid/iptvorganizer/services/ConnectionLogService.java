package org.anasoid.iptvorganizer.services;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.ConnectionLog;
import org.anasoid.iptvorganizer.repositories.ConnectionLogRepository;

@ApplicationScoped
public class ConnectionLogService extends BaseService<ConnectionLog, ConnectionLogRepository> {

  @Inject ConnectionLogRepository repository;

  @Override
  protected ConnectionLogRepository getRepository() {
    return repository;
  }

  @Override
  public Uni<Long> create(ConnectionLog log) {
    if (log.getClientId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Client ID is required"));
    }
    if (log.getAction() == null || log.getAction().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Action is required"));
    }
    if (log.getIpAddress() == null || log.getIpAddress().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("IP address is required"));
    }
    return repository.insert(log);
  }
}
