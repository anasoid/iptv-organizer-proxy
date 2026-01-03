package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.Category;
import org.anasoid.iptvorganizer.repositories.CategoryRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CategoryService extends BaseService<Category, CategoryRepository> {

    @Inject
    CategoryRepository repository;

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
}
