package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.repositories.stream.SeriesRepository;

@ApplicationScoped
public class SeriesService extends BaseStreamService<Series, SeriesRepository> {

  @Inject SeriesRepository repository;

  @Override
  protected SeriesRepository getRepository() {
    return repository;
  }
}
