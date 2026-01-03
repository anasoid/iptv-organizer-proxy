package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.Filter;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FilterService extends BaseService<Filter, FilterRepository> {

    @Inject
    FilterRepository repository;

    @Override
    protected FilterRepository getRepository() {
        return repository;
    }

    @Override
    public Uni<Long> create(Filter filter) {
        if (filter.getName() == null || filter.getName().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
        }
        if (filter.getFilterConfig() == null || filter.getFilterConfig().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Filter config is required"));
        }
        return repository.insert(filter);
    }
}
