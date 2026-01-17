package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class VodStreamService extends BaseService<VodStream, VodStreamRepository> {

  @Inject VodStreamRepository repository;

  @Override
  protected VodStreamRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(VodStream stream) {
    if (stream.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return repository.insert(stream);
  }
}
