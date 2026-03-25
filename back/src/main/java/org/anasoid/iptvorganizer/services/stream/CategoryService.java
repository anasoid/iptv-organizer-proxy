package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.repositories.stream.CategoryRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class CategoryService extends BaseService<Category, CategoryRepository> {

  @Inject CategoryRepository repository;

  @Override
  protected CategoryRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Category category) {
    if (category.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (category.getName() == null || category.getName().isBlank()) {
      throw new IllegalArgumentException("Category name is required");
    }
    if (category.getType() == null || category.getType().isBlank()) {
      throw new IllegalArgumentException("Category type is required");
    }
    return repository.insert(category);
  }

  /** Find categories by source ID with optional filters */
  public List<Category> findBySourceIdFiltered(
      Long sourceId,
      String categoryType,
      String search,
      String allowDenyFilter,
      String blackListFilter,
      int page,
      int limit) {
    return repository.findBySourceIdFiltered(
        sourceId, categoryType, search, allowDenyFilter, blackListFilter, page, limit);
  }

  /** Count categories by source ID with optional filters */
  public Long countBySourceIdFiltered(
      Long sourceId,
      String categoryType,
      String search,
      String allowDenyFilter,
      String blackListFilter) {
    return repository.countBySourceIdFiltered(
        sourceId, categoryType, search, allowDenyFilter, blackListFilter);
  }

  /** Find all categories by source and type */
  public List<Category> findBySourceAndType(Long sourceId, String type) {
    return findBySourceIdFiltered(sourceId, type, null, null, null, 1, Integer.MAX_VALUE);
  }

  /** Find categories by source and type with pagination */
  public List<Category> findBySourceAndTypePaged(
      Long sourceId, String type, int limit, int offset) {
    // Convert offset to page number: page = (offset / limit) + 1
    int page = offset > 0 ? (offset / limit) + 1 : 1;
    return findBySourceIdFiltered(sourceId, type, null, null, null, page, limit);
  }

  /** Find category by source, external category_id, and type */
  public Category findBySourceAndCategoryId(Long sourceId, Integer categoryId, String type) {
    return repository.findBySourceCategoryType(sourceId, categoryId, type);
  }
}
