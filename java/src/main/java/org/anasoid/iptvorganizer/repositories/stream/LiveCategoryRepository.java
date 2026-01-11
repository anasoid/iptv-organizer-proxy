package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class LiveCategoryRepository extends AbstractTypedCategoryRepository {

  @Override
  public StreamType getCategoryType() {
    return StreamType.LIVE;
  }
}
