package org.anasoid.iptvorganizer.services;

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
  public Long create(ConnectionLog log) {
    if (log.getClientId() == null) {
      throw new IllegalArgumentException("Client ID is required");
    }
    if (log.getAction() == null || log.getAction().isBlank()) {
      throw new IllegalArgumentException("Action is required");
    }
    if (log.getIpAddress() == null || log.getIpAddress().isBlank()) {
      throw new IllegalArgumentException("IP address is required");
    }
    return repository.insert(log);
  }
}
