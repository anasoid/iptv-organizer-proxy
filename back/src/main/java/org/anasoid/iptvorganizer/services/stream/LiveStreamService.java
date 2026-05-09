package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;

@ApplicationScoped
public class LiveStreamService extends BaseStreamService<LiveStream, LiveStreamRepository> {

  @Inject LiveStreamRepository repository;

  @Override
  protected LiveStreamRepository getRepository() {
    return repository;
  }
}
