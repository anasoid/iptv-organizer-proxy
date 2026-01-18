package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
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
  Map<Integer, Long> findIdsByExternalIds(List<Integer> externalIds, Long sourceId);

  /**
   * Find full entities by external IDs in bulk using IN clause. Returns a map of external_id ->
   * full entity for change detection. This method should be implemented by subclasses that extend
   * SourcedEntityRepository or provide their own bulk lookup implementation.
   */
  Map<Integer, T> findEntitiesByExternalIds(List<Integer> externalIds, Long sourceId);

  /**
   * Check if entity has any functional field changes compared to existing. Excludes id and
   * timestamps. Must be implemented by subclasses for their specific entity types.
   *
   * @param newEntity Entity with new data
   * @param existingEntity Entity from database
   * @return true if any functional field has changed, false otherwise
   */
  boolean hasFunctionalChanges(T newEntity, T existingEntity);

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

  int insertOrUpdateByExternalId(List<T> entities);

  /**
   * Insert or update multiple entities in a single transaction. Uses bulk SELECT to find existing
   * records, then inserts or updates each one. Implements change detection to skip unnecessary
   * UPDATE operations when entity data hasn't changed. Much more efficient than calling
   * insertOrUpdateByExternalId for each item individually.
   *
   * @param entities List of entities to save
   * @return Number of entities that were inserted (not updated)
   */
  @Transactional
  default int internalInsertOrUpdateByExternalId(List<T> entities) {
    if (entities.isEmpty()) {
      return 0;
    }

    // Get source ID from first entity (all should have same source)
    Long sourceId = entities.get(0).getSourceId();

    // Bulk load existing entity IDs using IN clause
    List<Integer> externalIds =
        entities.stream().map(SourcedEntity::getExternalId).filter(Objects::nonNull).toList();

    Map<Integer, Long> existingIdMap = findIdsByExternalIds(externalIds, sourceId);

    // Bulk fetch full entities for change detection
    List<Integer> existingExternalIds = new java.util.ArrayList<>(existingIdMap.keySet());
    Map<Integer, T> existingEntitiesMap =
        existingExternalIds.isEmpty()
            ? Map.of()
            : findEntitiesByExternalIds(existingExternalIds, sourceId);

    int insertCount = 0;
    int skippedCount = 0;

    // Process each entity within this transaction
    for (T entity : entities) {
      Long existingId = existingIdMap.get(entity.getExternalId());
      if (existingId != null) {
        // Update existing - but only if entity has changed
        entity.setId(existingId);

        // Check if any functional field has changed
        T existingEntity = existingEntitiesMap.get(entity.getExternalId());
        if (existingEntity != null && !hasFunctionalChanges(entity, existingEntity)) {
          skippedCount++; // Skip update - no changes detected
          continue;
        }

        update(entity); // Only update if changes detected
      } else {
        // Insert new
        insert(entity);
        insertCount++;
      }
    }

    return insertCount;
  }
}
