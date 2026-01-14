package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public abstract class AbstractTypedCategoryRepository
    implements SynchronizedItemRepository<Category> {

  @Inject protected CategoryRepository categoryRepository;

  @Override
  public Uni<Long> insert(Category category) {
    return categoryRepository.insert(category);
  }

  @Override
  public Uni<Void> update(Category category) {
    if (category.getType() == null) {
      category.setType(getType().getCategoryType());
    } else if (category.getType().equals(getType().getCategoryType())) {
      throw new IllegalArgumentException("Category type does not match");
    }
    return categoryRepository.update(category);
  }

  /**
   * Get or create "Unknown" category for a source and type Used when streams have no category
   * assigned
   *
   * @param sourceId Source ID
   * @param categoryType Category type (live, vod, series)
   * @return Database ID of the Unknown category
   */
  public Uni<Integer> getOrCreateUnknownCategory(Long sourceId) {
    return categoryRepository.getOrCreateUnknownCategory(sourceId, getType().getCategoryType());
  }

  public abstract StreamType getType();

  @Override
  public Uni<Category> findById(Long id) {
    return categoryRepository.findById(id);
  }

  @Override
  public Multi<Category> findBySourceId(Long sourceId) {
    return categoryRepository.findBySourceId(sourceId);
  }

  @Override
  public Multi<Integer> findExternalIdsBySourceId(Long sourceId) {
    return categoryRepository.findExternalIdsBySourceIdAndType(sourceId, getType());
  }

  @Override
  public Uni<Category> findByExternalId(Integer externalId, Long sourceId) {
    return categoryRepository.findByExternalIdAndType(externalId, sourceId, getType());
  }

  @Override
  public Uni<Void> deleteByExternalId(Integer externalId, Long sourceId) {
    return categoryRepository.deleteByExternalIdAndType(externalId, sourceId, getType());
  }

  @Override
  public Uni<Void> delete(Long id) {
    return categoryRepository.delete(id);
  }
}
