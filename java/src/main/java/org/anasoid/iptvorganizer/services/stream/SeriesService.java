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

  /** Find streams by source and category - stub for future database query implementation */
  public java.util.List<Series> findBySourceAndCategory(
      Long sourceId, Integer categoryId, int limit) {
    return java.util.Collections.emptyList();
  }

  /** Find stream by source and stream_id - stub for future database query implementation */
  public Series findBySourceAndStreamId(Long sourceId, Integer streamId) {
    return null;
  }
}
