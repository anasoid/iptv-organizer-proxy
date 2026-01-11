package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.util.Map;
import java.util.Set;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;

public interface SynchronizedItemRepository<T extends SourcedEntity> {

  /** Find entities by source ID */
  Multi<T> findBySourceId(Long sourceId);

  /** Find entities by source ID */
  Uni<Set<Integer>> findExternalIdsBySourceId(Long sourceId);

  Uni<T> findById(Long id);

  abstract Uni<Long> insert(T entity);

  abstract Uni<Void> update(T entity);

  Uni<T> findByExternalId(Integer externalId, Long sourceId);

  Uni<Void> deleteByExternalId(Integer externalId, Long sourceId);

  Uni<Void> delete(Long id);

  Multi<Map> fetchExternalData(Source source);
}
