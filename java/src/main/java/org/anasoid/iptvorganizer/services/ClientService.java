package org.anasoid.iptvorganizer.services;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.Client;
import org.anasoid.iptvorganizer.repositories.ClientRepository;

@ApplicationScoped
public class ClientService extends BaseService<Client, ClientRepository> {

  @Inject ClientRepository repository;

  @Override
  protected ClientRepository getRepository() {
    return repository;
  }

  @Override
  public Uni<Long> create(Client client) {
    if (client.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (client.getUsername() == null || client.getUsername().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Username is required"));
    }
    if (client.getPassword() == null || client.getPassword().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Password is required"));
    }
    if (client.getIsActive() == null) {
      client.setIsActive(true);
    }
    return repository.insert(client);
  }

  /** Search clients by name/email/username */
  public Multi<Client> searchClients(String search, int page, int limit) {
    return repository.searchClients(search, page, limit);
  }

  /** Count clients matching search criteria */
  public Uni<Long> countSearchClients(String search) {
    return repository.countSearchClients(search);
  }
}
