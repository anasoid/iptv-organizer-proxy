package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.BaseEntity;
import org.anasoid.iptvorganizer.repositories.BaseRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

public abstract class BaseService<T extends BaseEntity, R extends BaseRepository<T>> {

    protected abstract R getRepository();

    public Uni<T> getById(Long id) {
        return getRepository().findById(id);
    }

    public Multi<T> getAll() {
        return getRepository().findAll();
    }

    public abstract Uni<Long> create(T entity);

    public Uni<Void> update(T entity) {
        if (entity.getId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("ID is required for update"));
        }
        return getRepository().update(entity);
    }

    public Uni<Void> delete(Long id) {
        return getRepository().delete(id);
    }
}
