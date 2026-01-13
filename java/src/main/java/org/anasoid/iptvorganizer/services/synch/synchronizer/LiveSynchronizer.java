package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.stream.LiveCategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;
import org.anasoid.iptvorganizer.services.synch.mapper.AbstractSyncMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.LiveSyncMapper;

@ApplicationScoped
public class LiveSynchronizer extends AbstractSynchronizer<LiveStream> {
  @Inject private LiveSyncMapper liveSyncMapper;

  protected LiveSynchronizer() {}

  @Inject
  public LiveSynchronizer(
      LiveStreamRepository streamRepository, LiveCategoryRepository typedCategoryRepository) {
    super(streamRepository, typedCategoryRepository);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.LIVE;
  }

  @Override
  AbstractSyncMapper<LiveStream> getMapper() {
    return liveSyncMapper;
  }
}
