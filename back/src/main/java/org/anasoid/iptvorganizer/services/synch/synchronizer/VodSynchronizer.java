package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.stream.VodCategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;
import org.anasoid.iptvorganizer.services.synch.mapper.AbstractSyncMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.VodSyncMapper;

@ApplicationScoped
public class VodSynchronizer extends AbstractSynchronizer<VodStream> {
  @Inject private VodSyncMapper VodSyncMapper;

  protected VodSynchronizer() {}

  @Inject
  public VodSynchronizer(
      VodStreamRepository streamRepository, VodCategoryRepository typedCategoryRepository) {
    super(streamRepository, typedCategoryRepository);
  }

  @Override
  StreamType getStreamType() {
    return StreamType.VOD;
  }

  @Override
  AbstractSyncMapper<VodStream> getMapper() {
    return VodSyncMapper;
  }
}
