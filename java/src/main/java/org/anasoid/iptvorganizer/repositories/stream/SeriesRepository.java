package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class SeriesRepository extends BaseStreamRepository<Series> {
  @Inject XtreamClient xtreamClient;

  @Override
  protected String getTableName() {
    return "series";
  }

  @Override
  public Multi<Map> fetchExternalData(Source source) {

    return xtreamClient.getSeries(source);
  }

  @Override
  public Uni<Long> insert(Series series) {
    String sql =
        "INSERT INTO series (source_id, external_id, num, allow_deny, name, category_id,"
            + " category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?)";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(series.getSourceId())
            .addInteger(series.getExternalId())
            .addInteger(series.getNum())
            .addString(series.getAllowDeny())
            .addString(series.getName())
            .addInteger(series.getCategoryId())
            .addString(series.getCategoryIds())
            .addBoolean(series.getIsAdult())
            .addString(series.getLabels())
            .addString(series.getData())
            .addLocalDate(series.getAddedDate())
            .addLocalDate(series.getReleaseDate());
    return pool.preparedQuery(sql).execute(tuple).map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(Series series) {
    String sql =
        "UPDATE series SET source_id = ?, external_id = ?, num = ?, allow_deny = ?, name = ?,"
            + " category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date ="
            + " ?, release_date = ? WHERE id = ?";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(series.getSourceId())
            .addInteger(series.getExternalId())
            .addInteger(series.getNum())
            .addString(series.getAllowDeny())
            .addString(series.getName())
            .addInteger(series.getCategoryId())
            .addString(series.getCategoryIds())
            .addBoolean(series.getIsAdult())
            .addString(series.getLabels())
            .addString(series.getData())
            .addLocalDate(series.getAddedDate())
            .addLocalDate(series.getReleaseDate())
            .addLong(series.getId());
    return pool.preparedQuery(sql).execute(tuple).replaceWithVoid();
  }

  @Override
  protected Series mapRow(Row row) {
    return Series.builder()
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
