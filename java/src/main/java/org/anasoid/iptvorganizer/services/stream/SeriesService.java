package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.repositories.stream.SeriesRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class SeriesService extends BaseService<Series, SeriesRepository> {

  @Inject SeriesRepository repository;

  @Override
  protected SeriesRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Series series) {
    if (series.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (series.getName() == null || series.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return repository.insert(series);
  }
}
