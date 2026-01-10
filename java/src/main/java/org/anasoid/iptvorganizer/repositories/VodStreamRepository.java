package org.anasoid.iptvorganizer.repositories;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.models.VodStream;

@ApplicationScoped
public class VodStreamRepository extends BaseRepository<VodStream> {

  @Override
  protected String getTableName() {
    return "vod_streams";
  }

  @Override
  public Uni<Long> insert(VodStream stream) {
    String sql =
        "INSERT INTO vod_streams (source_id, stream_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(stream.getSourceId())
            .addInteger(stream.getStreamId())
            .addInteger(stream.getNum())
            .addString(stream.getAllowDeny())
            .addString(stream.getName())
            .addInteger(stream.getCategoryId())
            .addString(stream.getCategoryIds())
            .addBoolean(stream.getIsAdult())
            .addString(stream.getLabels())
            .addString(stream.getData())
            .addLocalDate(stream.getAddedDate())
            .addLocalDate(stream.getReleaseDate());
    return pool.preparedQuery(sql).execute(tuple).map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(VodStream stream) {
    String sql =
        "UPDATE vod_streams SET source_id = ?, stream_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(stream.getSourceId())
            .addInteger(stream.getStreamId())
            .addInteger(stream.getNum())
            .addString(stream.getAllowDeny())
            .addString(stream.getName())
            .addInteger(stream.getCategoryId())
            .addString(stream.getCategoryIds())
            .addBoolean(stream.getIsAdult())
            .addString(stream.getLabels())
            .addString(stream.getData())
            .addLocalDate(stream.getAddedDate())
            .addLocalDate(stream.getReleaseDate())
            .addLong(stream.getId());
    return pool.preparedQuery(sql).execute(tuple).replaceWithVoid();
  }

  @Override
  protected VodStream mapRow(Row row) {
    return VodStream.builder()
        .id(row.getLong("id"))
        .sourceId(row.getLong("source_id"))
        .streamId(row.getInteger("stream_id"))
        .num(row.getInteger("num"))
        .allowDeny(row.getString("allow_deny"))
        .name(row.getString("name"))
        .categoryId(row.getInteger("category_id"))
        .categoryIds(row.getString("category_ids"))
        .isAdult(row.getBoolean("is_adult"))
        .labels(row.getString("labels"))
        .data(row.getString("data"))
        .addedDate(row.getLocalDate("added_date"))
        .releaseDate(row.getLocalDate("release_date"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
  }

  /**
   * Find existing VOD streams by a list of stream_ids for a specific source Used for batch upsert
   * operations during sync
   */
  public Multi<VodStream> findByStreamIds(Long sourceId, List<Integer> streamIds) {
    if (streamIds == null || streamIds.isEmpty()) {
      return Multi.createFrom().empty();
    }

    StringBuilder sql =
        new StringBuilder("SELECT * FROM vod_streams WHERE source_id = ? AND stream_id IN (");
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

  /**
   * Batch upsert VOD streams (update if exists by stream_id, insert if new) Uses ON DUPLICATE KEY
   * UPDATE for efficient batch processing
   */
  public Uni<Void> batchUpsert(List<VodStream> streams) {
    if (streams == null || streams.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    // Build INSERT ... ON DUPLICATE KEY UPDATE statement
    String sql =
        "INSERT INTO vod_streams (source_id, stream_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "num=VALUES(num), allow_deny=VALUES(allow_deny), name=VALUES(name), "
            + "category_id=VALUES(category_id), category_ids=VALUES(category_ids), "
            + "is_adult=VALUES(is_adult), labels=VALUES(labels), data=VALUES(data), "
            + "added_date=VALUES(added_date), release_date=VALUES(release_date)";

    return pool.preparedQuery(sql)
        .executeBatch(
            streams.stream()
                .map(
                    stream ->
                        Tuple.tuple()
                            .addLong(stream.getSourceId())
                            .addInteger(stream.getStreamId())
                            .addInteger(stream.getNum())
                            .addString(stream.getAllowDeny())
                            .addString(stream.getName())
                            .addInteger(stream.getCategoryId())
                            .addString(stream.getCategoryIds())
                            .addBoolean(stream.getIsAdult())
                            .addString(stream.getLabels())
                            .addString(stream.getData())
                            .addLocalDate(stream.getAddedDate())
                            .addLocalDate(stream.getReleaseDate()))
                .collect(Collectors.toList()))
        .replaceWithVoid();
  }
}
