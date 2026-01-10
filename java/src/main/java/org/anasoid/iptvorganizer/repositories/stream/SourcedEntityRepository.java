package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Tuple;
import org.anasoid.iptvorganizer.models.stream.SourcedEntity;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

/**
 * Base repository for entities that belong to a source and have ordering.
 *
 * @param <T> The entity type extending SourcedEntity
 */
public abstract class SourcedEntityRepository<T extends SourcedEntity> extends BaseRepository<T> {

  /** Find entities by source ID */
  public Multi<T> findBySourceId(Long sourceId) {
    return pool.preparedQuery(
            "SELECT * FROM " + getTableName() + " WHERE source_id = ? ORDER BY num ASC, id DESC")
        .execute(Tuple.of(sourceId))
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(this::mapRow);
  }

  /** Count entities by source ID */
  public Uni<Long> countBySourceId(Long sourceId) {
    return countWhere("source_id = ?", Tuple.of(sourceId));
  }
}
