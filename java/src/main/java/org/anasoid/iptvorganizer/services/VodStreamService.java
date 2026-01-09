package org.anasoid.iptvorganizer.services;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.VodStream;
import org.anasoid.iptvorganizer.repositories.VodStreamRepository;

@ApplicationScoped
public class VodStreamService extends BaseService<VodStream, VodStreamRepository> {

  @Inject VodStreamRepository repository;

  @Override
  protected VodStreamRepository getRepository() {
    return repository;
  }

  @Override
  public Uni<Long> create(VodStream stream) {
    if (stream.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
    }
    return repository.insert(stream);
  }
}
