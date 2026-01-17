package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class LiveStreamService extends BaseService<LiveStream, LiveStreamRepository> {

  @Inject LiveStreamRepository repository;

  @Override
  protected LiveStreamRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(LiveStream stream) {
    if (stream.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return repository.insert(stream);
  }
}
