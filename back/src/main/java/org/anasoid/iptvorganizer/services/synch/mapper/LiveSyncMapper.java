package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class LiveSyncMapper extends AbstractSyncMapper<LiveStream> {

  @Override
  public LiveStream mapToStream(SynchronizedItemMapParameter param) {
    LiveStream stream =
        LiveStream.builder().externalId(getIntValue(param.getData(), "stream_id")).build();

    return mapToStream(stream, param);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.LIVE;
  }
}
