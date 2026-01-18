package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Objects;
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

  /**
   * Check if BaseStream has any functional field changes compared to existing. Compares base
   * SourcedEntity fields plus BaseStream-specific fields (categoryId, allowDeny, name, categoryIds,
   * isAdult, labels, data, addedDate, releaseDate).
   *
   * @param newEntity BaseStream with new data
   * @param existingEntity BaseStream from database
   * @return true if any functional field has changed, false otherwise
   */
  @Override
  public boolean hasFunctionalChanges(T newEntity, T existingEntity) {
    // First check base SourcedEntity fields
    if (super.hasFunctionalChanges(newEntity, existingEntity)) {
      return true;
    }

    // Check BaseStream-specific fields
    if (!Objects.equals(newEntity.getCategoryId(), existingEntity.getCategoryId())) return true;
    if (!Objects.equals(newEntity.getAllowDeny(), existingEntity.getAllowDeny())) return true;
    if (!Objects.equals(newEntity.getName(), existingEntity.getName())) return true;
    if (!Objects.equals(newEntity.getCategoryIds(), existingEntity.getCategoryIds())) return true;
    if (!Objects.equals(newEntity.getIsAdult(), existingEntity.getIsAdult())) return true;
    if (!Objects.equals(newEntity.getLabels(), existingEntity.getLabels())) return true;
    if (!Objects.equals(newEntity.getData(), existingEntity.getData())) return true;
    if (!Objects.equals(newEntity.getAddedDate(), existingEntity.getAddedDate())) return true;
    if (!Objects.equals(newEntity.getReleaseDate(), existingEntity.getReleaseDate())) return true;

    return false;
  }
}
