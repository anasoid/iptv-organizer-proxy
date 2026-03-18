package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class SeriesSyncMapper extends AbstractSyncMapper<Series> {

  @Override
  public Series mapToStream(SynchronizedItemMapParameter param) {
    Series stream = Series.builder().externalId(getIntValue(param.getData(), "series_id")).build();

    return mapToStream(stream, param);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.SERIES;
  }
}
