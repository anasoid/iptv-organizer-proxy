package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class VodCategoryRepository extends AbstractTypedCategoryRepository {

  @Override
  public StreamType getCategoryType() {
    return StreamType.VOD;
  }
}
