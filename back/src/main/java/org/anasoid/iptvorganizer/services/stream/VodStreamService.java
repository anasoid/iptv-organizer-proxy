package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;

@ApplicationScoped
public class VodStreamService extends BaseStreamService<VodStream, VodStreamRepository> {

  @Inject VodStreamRepository repository;

  @Override
  protected VodStreamRepository getRepository() {
    return repository;
  }
}
