package org.anasoid.iptvorganizer.services.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.stream.*;
import org.anasoid.iptvorganizer.models.stream.Category;
import org.anasoid.iptvorganizer.repositories.stream.*;
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
  public Uni<Long> create(Category category) {
    if (category.getSourceId() == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
    }
    if (category.getCategoryName() == null || category.getCategoryName().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Category name is required"));
    }
    if (category.getCategoryType() == null || category.getCategoryType().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Category type is required"));
    }
    return repository.insert(category);
  }

  /** Find categories by source ID with optional filters */
  public Multi<Category> findBySourceIdFiltered(
      Long sourceId, String categoryType, String search, int page, int limit) {
    return repository.findBySourceIdFiltered(sourceId, categoryType, search, page, limit);
  }

  /** Count categories by source ID with optional filters */
  public Uni<Long> countBySourceIdFiltered(Long sourceId, String categoryType, String search) {
    return repository.countBySourceIdFiltered(sourceId, categoryType, search);
  }
}
