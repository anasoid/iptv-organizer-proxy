package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Tuple;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
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

  /** Find entities by source ID */
  public Multi<Integer> findExternalIdsBySourceId(Long sourceId) {
    return pool.preparedQuery("SELECT external_id FROM " + getTableName() + " WHERE source_id = ?")
        .execute(Tuple.of(sourceId))
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(row -> row.getInteger("external_id"));
  }

  public Uni<T> findByExternalId(Integer externalId, Long sourceId) {
    return pool.preparedQuery(
            "SELECT * FROM " + getTableName() + " WHERE external_id = ? AND source_id = ?")
        .execute(Tuple.of(externalId, sourceId))
        .map(rowSet -> rowSet.size() == 0 ? null : mapRow(rowSet.iterator().next()));
  }

  public Uni<Void> deleteByExternalId(Integer externalId, Long sourceId) {
    return pool.preparedQuery(
            "DELETE FROM " + getTableName() + " WHERE external_id = ? AND source_id = ?")
        .execute(Tuple.of(externalId, sourceId))
        .replaceWithVoid();
  }

  /** Count entities by source ID */
  public Uni<Long> countBySourceId(Long sourceId) {
    return countWhere("source_id = ?", Tuple.of(sourceId));
  }
}
