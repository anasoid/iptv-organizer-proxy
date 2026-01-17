package org.anasoid.iptvorganizer.repositories.stream;

import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;

public interface SynchronizedItemRepository<T extends SourcedEntity> {

  /** Find entities by source ID */
  List<T> findBySourceId(Long sourceId);

  /** Find external IDs by source ID */
  List<Integer> findExternalIdsBySourceId(Long sourceId);

  T findById(Long id);

  Long insert(T entity);

  void update(T entity);

  T findByExternalId(Integer externalId, Long sourceId);

  void deleteByExternalId(Integer externalId, Long sourceId);

  void delete(Long id);

  List<Map> fetchExternalData(Source source);

  default Boolean insertOrUpdateByExternalId(T entity) {
    T existing = findByExternalId(entity.getExternalId(), entity.getSourceId());
    if (existing != null) {
      entity.setId(existing.getId());
      update(entity);
      return Boolean.FALSE;
    } else {
      insert(entity);
      return Boolean.TRUE;
    }
  }
}
