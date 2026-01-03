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

    /**
     * Get total count of records in the table
     */
    public Uni<Long> count() {
        return pool.query("SELECT COUNT(*) as cnt FROM " + getTableName())
            .execute()
            .map(rowSet -> rowSet.iterator().hasNext() ? rowSet.iterator().next().getLong("cnt") : 0L);
    }

    /**
     * Get paginated results
     */
    public Multi<T> findAllPaged(int page, int limit) {
        int offset = (page - 1) * limit;
        String sql = "SELECT * FROM " + getTableName() + " LIMIT ? OFFSET ?";
        return pool.preparedQuery(sql)
            .execute(Tuple.of(limit, offset))
            .onItem()
            .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .map(this::mapRow);
    }

    /**
     * Count records matching a where clause
     */
    protected Uni<Long> countWhere(String whereClause, Tuple params) {
        String sql = "SELECT COUNT(*) as cnt FROM " + getTableName() + " WHERE " + whereClause;
        return pool.preparedQuery(sql)
            .execute(params)
            .map(rowSet -> rowSet.iterator().hasNext() ? rowSet.iterator().next().getLong("cnt") : 0L);
    }

    /**
     * Find records with where clause and pagination
     */
    protected Multi<T> findWherePaged(String whereClause, Tuple params, int page, int limit, String orderBy) {
        int offset = (page - 1) * limit;
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(getTableName())
            .append(" WHERE ").append(whereClause);
        if (orderBy != null && !orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        sql.append(" LIMIT ? OFFSET ?");

        // Add limit and offset to params
        Tuple finalParams = params.addInteger(limit).addInteger(offset);

        return pool.preparedQuery(sql.toString())
            .execute(finalParams)
            .onItem()
            .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .map(this::mapRow);
    }
}
