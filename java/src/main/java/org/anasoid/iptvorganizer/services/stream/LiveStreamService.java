package org.anasoid.iptvorganizer.services.stream;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.stream.LiveStream;
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
  public Uni<Long> create(LiveStream stream) {
    if (stream.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
    }
    return repository.insert(stream);
  }
}
