package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class LiveStreamRepository extends BaseStreamRepository<LiveStream> {

  @Inject XtreamClient xtreamClient;

  @Override
  protected String getTableName() {
    return "live_streams";
  }

  @Override
  public Multi<Map> fetchExternalData(Source source) {

    return xtreamClient.getLiveStreams(source);
  }

  @Override
  public Uni<Long> insert(LiveStream stream) {
    String sql =
        "INSERT INTO live_streams (source_id, external_id, num, allow_deny, name, category_id,"
            + " category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?)";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(stream.getSourceId())
            .addInteger(stream.getExternalId())
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
  public Uni<Void> update(LiveStream stream) {
    String sql =
        "UPDATE live_streams SET source_id = ?, external_id = ?, num = ?, allow_deny = ?, name = ?,"
            + " category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date ="
            + " ?, release_date = ? WHERE id = ?";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(stream.getSourceId())
            .addInteger(stream.getExternalId())
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
  protected LiveStream mapRow(Row row) {
    return LiveStream.builder()
        .id(row.getLong("id"))
        .sourceId(row.getLong("source_id"))
        .externalId(row.getInteger("external_id"))
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
}
