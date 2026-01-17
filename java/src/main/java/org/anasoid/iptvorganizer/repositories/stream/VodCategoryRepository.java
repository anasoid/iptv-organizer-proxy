package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class VodCategoryRepository extends AbstractTypedCategoryRepository {

  @Inject XtreamClient xtreamClient;

  @Override
  public StreamType getType() {
    return StreamType.VOD;
  }

  @Override
  public JsonStreamResult<Map> fetchExternalData(Source source) {
    return xtreamClient.getVodCategories(source);
  }
}
