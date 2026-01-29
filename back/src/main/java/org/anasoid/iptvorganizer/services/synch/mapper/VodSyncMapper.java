package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;

@ApplicationScoped
public class VodSyncMapper extends AbstractSyncMapper<VodStream> {

  @Override
  public VodStream mapToStream(SynchronizedItemMapParameter param) {
    VodStream stream =
        VodStream.builder().externalId(getIntValue(param.getData(), "stream_id")).build();

    return mapToStream(stream, param);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.VOD;
  }
}
