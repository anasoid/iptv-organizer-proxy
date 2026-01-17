package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.repositories.ClientRepository;

@ApplicationScoped
public class ClientService extends BaseService<Client, ClientRepository> {

  @Inject ClientRepository repository;

  @Override
  protected ClientRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Client client) {
    if (client.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (client.getUsername() == null || client.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username is required");
    }
    if (client.getPassword() == null || client.getPassword().isBlank()) {
      throw new IllegalArgumentException("Password is required");
    }
    if (client.getIsActive() == null) {
      client.setIsActive(true);
    }
    return repository.insert(client);
  }

  /** Search clients by name/email/username */
  public List<Client> searchClients(String search, int page, int limit) {
    return repository.searchClients(search, page, limit);
  }

  /** Count clients matching search criteria */
  public Long countSearchClients(String search) {
    return repository.countSearchClients(search);
  }
}
