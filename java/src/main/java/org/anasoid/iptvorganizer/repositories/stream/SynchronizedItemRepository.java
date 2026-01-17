package org.anasoid.iptvorganizer.repositories.stream;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  /**
   * Find existing entities by external IDs in bulk using IN clause. Returns a map of external_id ->
   * entity for quick lookup. This method should be implemented by subclasses that extend
   * SourcedEntityRepository or provide their own bulk lookup implementation.
   */
  default Map<Integer, T> findByExternalIds(List<Integer> externalIds, Long sourceId) {
    throw new UnsupportedOperationException("findByExternalIds must be implemented by subclass");
  }

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

  /**
   * Insert or update multiple entities in a single transaction. Uses bulk SELECT to find existing
   * records, then inserts or updates each one. Much more efficient than calling
   * insertOrUpdateByExternalId for each item individually.
   *
   * @param entities List of entities to save
   * @return Number of entities that were inserted (not updated)
   */
  default int insertOrUpdateByExternalId(List<T> entities) {
    if (entities.isEmpty()) {
      return 0;
    }

    // Get source ID from first entity (all should have same source)
    Long sourceId = entities.get(0).getSourceId();

    // Bulk load existing entities using IN clause
    List<Integer> externalIds =
        entities.stream().map(SourcedEntity::getExternalId).filter(Objects::nonNull).toList();

    Map<Integer, T> existingMap = findByExternalIds(externalIds, sourceId);

    int insertCount = 0;

    // Process each entity within this transaction
    for (T entity : entities) {
      T existing = existingMap.get(entity.getExternalId());
      if (existing != null) {
        // Update existing
        entity.setId(existing.getId());
        update(entity);
      } else {
        // Insert new
        insert(entity);
        insertCount++;
      }
    }

    return insertCount;
  }
}
