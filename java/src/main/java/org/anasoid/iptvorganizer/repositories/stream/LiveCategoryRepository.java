package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class LiveCategoryRepository extends AbstractTypedCategoryRepository {

  @Inject XtreamClient xtreamClient;

  @Override
  public StreamType getType() {
    return StreamType.LIVE;
  }

  @Override
  public Multi<Map> fetchExternalData(Source source) {

    return xtreamClient.getLiveCategories(source);
  }
}
