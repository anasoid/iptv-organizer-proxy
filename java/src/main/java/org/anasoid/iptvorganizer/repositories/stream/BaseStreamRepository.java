package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Tuple;
import java.util.List;
import org.anasoid.iptvorganizer.models.stream.BaseStream;

/**
 * Base repository for stream-like entities (LiveStream, VodStream, Series).
 *
 * @param <T> The stream type extending BaseStream
 */
public abstract class BaseStreamRepository<T extends BaseStream>
    extends SourcedEntityRepository<T> {

  /** Batch upsert streams (update if exists by stream_id, insert if new) */
  public abstract Uni<Void> batchUpsert(List<T> entities);

  /** Find existing streams by a list of stream_ids for a specific source */
  public Multi<T> findByStreamIds(Long sourceId, List<Integer> streamIds) {
    if (streamIds == null || streamIds.isEmpty()) {
      return Multi.createFrom().empty();
    }

    StringBuilder sql =
        new StringBuilder("SELECT * FROM ")
            .append(getTableName())
            .append(" WHERE source_id = ? AND stream_id IN (");
    for (int i = 0; i < streamIds.size(); i++) {
      if (i > 0) sql.append(", ");
      sql.append("?");
    }
    sql.append(")");

    Tuple tuple = Tuple.of(sourceId);
    for (Integer streamId : streamIds) {
      tuple = tuple.addInteger(streamId);
    }

    return pool.preparedQuery(sql.toString())
        .execute(tuple)
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(this::mapRow);
  }
}
