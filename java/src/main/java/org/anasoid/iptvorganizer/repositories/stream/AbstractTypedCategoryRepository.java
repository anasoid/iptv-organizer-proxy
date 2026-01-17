package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public abstract class AbstractTypedCategoryRepository
    implements SynchronizedItemRepository<Category> {

  @Inject protected CategoryRepository categoryRepository;

  @Override
  public Long insert(Category category) {
    return categoryRepository.insert(category);
  }

  @Override
  public void update(Category category) {
    if (category.getType() == null) {
      category.setType(getType().getCategoryType());
    } else if (!category.getType().equals(getType().getCategoryType())) {
      throw new IllegalArgumentException(
          "Category type does not match : "
              + category.getType()
              + "<>"
              + getType().getCategoryType());
    }
    categoryRepository.update(category);
  }

  /**
   * Get or create "Unknown" category for a source and type Used when streams have no category
   * assigned
   *
   * @param sourceId Source ID
   * @return Database ID of the Unknown category
   */
  public Integer getOrCreateUnknownCategory(Long sourceId) {
    return categoryRepository.getOrCreateUnknownCategory(sourceId, getType().getCategoryType());
  }

  public abstract StreamType getType();

  @Override
  public Category findById(Long id) {
    return categoryRepository.findById(id);
  }

  @Override
  public List<Category> findBySourceId(Long sourceId) {
    return categoryRepository.findBySourceId(sourceId);
  }

  @Override
  public List<Integer> findExternalIdsBySourceId(Long sourceId) {
    return categoryRepository.findExternalIdsBySourceIdAndType(sourceId, getType());
  }

  @Override
  public Category findByExternalId(Integer externalId, Long sourceId) {
    return categoryRepository.findByExternalIdAndType(externalId, sourceId, getType());
  }

  @Override
  public void deleteByExternalId(Integer externalId, Long sourceId) {
    categoryRepository.deleteByExternalIdAndType(externalId, sourceId, getType());
  }

  @Override
  public void delete(Long id) {
    categoryRepository.delete(id);
  }

  @Override
  public Map<Integer, Category> findByExternalIds(List<Integer> externalIds, Long sourceId) {
    // Use the bulk find method from CategoryRepository, then filter by type
    Map<Integer, Category> allFound = categoryRepository.findByExternalIds(externalIds, sourceId);

    // Filter to only include categories of this type
    return allFound.entrySet().stream()
        .filter(e -> e.getValue().getType().equals(getType().getCategoryType()))
        .collect(
            java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
  }
}
