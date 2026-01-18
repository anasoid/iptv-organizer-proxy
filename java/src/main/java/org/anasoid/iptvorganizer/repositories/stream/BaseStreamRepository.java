package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;

/**
 * Base repository for stream-like entities (LiveStream, VodStream, Series).
 *
 * @param <T> The stream type extending BaseStream
 */
public abstract class BaseStreamRepository<T extends BaseStream> extends SourcedEntityRepository<T>
    implements SynchronizedItemRepository<T> {

  @Override
  @Transactional
  public int insertOrUpdateByExternalId(List<T> entities) {
    return internalInsertOrUpdateByExternalId(entities);
  }
}
