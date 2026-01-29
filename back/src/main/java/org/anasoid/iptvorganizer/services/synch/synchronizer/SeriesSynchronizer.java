package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.stream.SeriesCategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.SeriesRepository;
import org.anasoid.iptvorganizer.services.synch.mapper.AbstractSyncMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.SeriesSyncMapper;

@ApplicationScoped
public class SeriesSynchronizer extends AbstractSynchronizer<Series> {
  @Inject private SeriesSyncMapper SeriesSyncMapper;

  protected SeriesSynchronizer() {}

  @Inject
  public SeriesSynchronizer(
      SeriesRepository streamRepository, SeriesCategoryRepository typedCategoryRepository) {
    super(streamRepository, typedCategoryRepository);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.SERIES;
  }

  @Override
  AbstractSyncMapper<Series> getMapper() {
    return SeriesSyncMapper;
  }
}
