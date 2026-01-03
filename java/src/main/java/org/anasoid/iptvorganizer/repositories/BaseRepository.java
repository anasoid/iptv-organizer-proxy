package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.BaseEntity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;

public abstract class BaseRepository<T extends BaseEntity> {

    @Inject
    protected Pool pool;

    protected abstract String getTableName();

    protected abstract T mapRow(Row row);

    public Uni<T> findById(Long id) {
        return pool.preparedQuery("SELECT * FROM " + getTableName() + " WHERE id = ?")
            .execute(Tuple.of(id))
            .map(rowSet -> rowSet.size() == 0 ? null : mapRow(rowSet.iterator().next()));
    }

    public Multi<T> findAll() {
        return pool.query("SELECT * FROM " + getTableName())
            .execute()
            .onItem()
            .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .map(this::mapRow);
    }

    public abstract Uni<Long> insert(T entity);

    public abstract Uni<Void> update(T entity);

    public Uni<Void> delete(Long id) {
        return pool.preparedQuery("DELETE FROM " + getTableName() + " WHERE id = ?")
            .execute(Tuple.of(id))
            .replaceWithVoid();
    }
}
