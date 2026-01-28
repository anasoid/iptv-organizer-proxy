package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.repositories.ProxyRepository;

@ApplicationScoped
public class ProxyService extends BaseService<Proxy, ProxyRepository> {

  @Inject ProxyRepository repository;

  @Override
  protected ProxyRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Proxy proxy) {
    if (proxy.getName() == null || proxy.getName().isBlank()) {
      throw new IllegalArgumentException("Proxy name is required");
    }

    // Validate that either proxyUrl or proxyHost is provided
    if ((proxy.getProxyUrl() == null || proxy.getProxyUrl().isBlank())
        && (proxy.getProxyHost() == null || proxy.getProxyHost().isBlank())) {
      throw new IllegalArgumentException("Either proxy URL or proxy host must be provided");
    }

    // Check if name already exists
    if (repository.nameExists(proxy.getName())) {
      throw new IllegalArgumentException("Proxy name already exists");
    }

    return repository.insert(proxy);
  }

  /** Check if proxy name exists */
  public Boolean nameExists(String name) {
    return repository.nameExists(name);
  }
}
