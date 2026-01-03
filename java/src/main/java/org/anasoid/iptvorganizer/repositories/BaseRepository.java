package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.BaseEntity;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    /**
     * Chunk a list into smaller sublists
     * Useful for batch processing to avoid MySQL query size limits
     */
    protected <E> List<List<E>> chunkList(List<E> list, int chunkSize) {
        List<List<E>> chunks = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return chunks;
        }
        for (int i = 0; i < list.size(); i += chunkSize) {
            int end = Math.min(list.size(), i + chunkSize);
            chunks.add(new ArrayList<>(list.subList(i, end)));
        }
        return chunks;
    }

    /**
     * Execute a batch upsert operation with automatic chunking
     * Chunks large batches to avoid exceeding MySQL query parameter limits
     *
     * @param entities List of entities to upsert
     * @param valueTupleMapper Function to convert entity to Tuple of values
     * @param baseInsertSql SQL template: "INSERT INTO table (col1, col2, ...) VALUES "
     * @param updateClause SQL clause: " ON DUPLICATE KEY UPDATE col1 = VALUES(col1), ..."
     * @return Uni that completes when all chunks are upserted
     */
    protected Uni<Void> executeBatchUpsert(List<T> entities,
                                           Function<T, Tuple> valueTupleMapper,
                                           String baseInsertSql,
                                           String updateClause) {
        if (entities == null || entities.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        // Chunk into batches of 500 to avoid query parameter limits
        // MySQL max_allowed_packet and parameter limits
        List<List<T>> chunks = chunkList(entities, 500);

        return Multi.createFrom().iterable(chunks)
            .onItem().transformToUniAndConcatenate(chunk ->
                executeSingleBatchUpsert(chunk, valueTupleMapper, baseInsertSql, updateClause)
            )
            .collect().asList()
            .replaceWithVoid();
    }

    /**
     * Execute a single batch upsert (one chunk)
     */
    private Uni<Void> executeSingleBatchUpsert(List<T> batch,
                                               Function<T, Tuple> valueTupleMapper,
                                               String baseInsertSql,
                                               String updateClause) {
        if (batch.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        StringBuilder sql = new StringBuilder(baseInsertSql);
        Tuple params = Tuple.tuple();

        // Build multi-value statement
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sql.append(", ");

            T entity = batch.get(i);
            Tuple valueTuple = valueTupleMapper.apply(entity);

            // Add placeholders (assumes all tuples have same size)
            sql.append("(");
            for (int j = 0; j < valueTuple.size(); j++) {
                if (j > 0) sql.append(", ");
                sql.append("?");
                params.addValue(valueTuple.getValue(j));
            }
            sql.append(")");
        }

        sql.append(updateClause);

        return pool.preparedQuery(sql.toString())
            .execute(params)
            .replaceWithVoid();
    }
}
