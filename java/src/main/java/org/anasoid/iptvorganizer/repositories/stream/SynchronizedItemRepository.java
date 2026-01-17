package org.anasoid.iptvorganizer.repositories.stream;

import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;

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

  /**
   * Fetch external data from source using lazy Iterator-based streaming.
   *
   * @param source The source to fetch data from
   * @return JsonStreamResult with lazy Iterator for streaming items
   */
  JsonStreamResult<Map> fetchExternalData(Source source);

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
