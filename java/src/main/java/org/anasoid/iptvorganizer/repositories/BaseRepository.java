package org.anasoid.iptvorganizer.repositories;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.models.BaseEntity;

public abstract class BaseRepository<T extends BaseEntity> {

  @Inject protected Pool pool;

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

  /** Get total count of records in the table */
  public Uni<Long> count() {
    return pool.query("SELECT COUNT(*) as cnt FROM " + getTableName())
        .execute()
        .map(rowSet -> rowSet.iterator().hasNext() ? rowSet.iterator().next().getLong("cnt") : 0L);
  }

  /** Get paginated results */
  public Multi<T> findAllPaged(int page, int limit) {
    int offset = (page - 1) * limit;
    String sql = "SELECT * FROM " + getTableName() + " LIMIT ? OFFSET ?";
    return pool.preparedQuery(sql)
        .execute(Tuple.of(limit, offset))
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(this::mapRow);
  }

  /** Count records matching a where clause */
  protected Uni<Long> countWhere(String whereClause, Tuple params) {
    String sql = "SELECT COUNT(*) as cnt FROM " + getTableName() + " WHERE " + whereClause;
    return pool.preparedQuery(sql)
        .execute(params)
        .map(rowSet -> rowSet.iterator().hasNext() ? rowSet.iterator().next().getLong("cnt") : 0L);
  }

  /** Find records with where clause and pagination */
  protected Multi<T> findWherePaged(
      String whereClause, Tuple params, int page, int limit, String orderBy) {
    int offset = (page - 1) * limit;
    StringBuilder sql =
        new StringBuilder("SELECT * FROM ")
            .append(getTableName())
            .append(" WHERE ")
            .append(whereClause);
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
   * Get the inserted ID from a RowSet in a database-agnostic way. Handles both reactive
   * MySQL/PostgreSQL (via LAST_INSERTED_ID property) and JDBC/H2 databases.
   */
  protected Long getInsertedId(io.vertx.mutiny.sqlclient.RowSet<Row> rowSet) {
    try {
      // Try MySQL-specific property first (for reactive MySQL/PostgreSQL drivers)
      Object lastInsertedId =
          rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID);
      if (lastInsertedId != null) {
        return (Long) lastInsertedId;
      }
    } catch (Exception e) {
      // MySQLClient not available or property doesn't exist, fall through to JDBC approach
    }

    // For JDBC-based databases (H2), try to get generated key from first row
    if (rowSet.iterator().hasNext()) {
      Row row = rowSet.iterator().next();
      try {
        // Try to get the first column which should be the generated ID
        return row.getLong(0);
      } catch (Exception e) {
        // Fall through
      }
    }

    throw new IllegalStateException("Unable to retrieve inserted ID from RowSet");
  }

  /**
   * Find IDs only for a specific source_id (lightweight query) Used for delete detection in sync
   * operations
   */
  public Multi<Long> findIdsBySourceId(Long sourceId) {
    String sql = "SELECT id FROM " + getTableName() + " WHERE source_id = ?";
    return pool.preparedQuery(sql)
        .execute(Tuple.of(sourceId))
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(row -> row.getLong("id"));
  }

  /** Delete multiple entities by IDs in batch Used for cleanup of obsolete records during sync */
  public Uni<Void> batchDeleteByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    StringBuilder sql = new StringBuilder("DELETE FROM " + getTableName() + " WHERE id IN (");
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) sql.append(", ");
      sql.append("?");
    }
    sql.append(")");

    Tuple tuple = Tuple.tuple();
    for (Long id : ids) {
      tuple = tuple.addLong(id);
    }

    return pool.preparedQuery(sql.toString()).execute(tuple).replaceWithVoid();
  }
}
