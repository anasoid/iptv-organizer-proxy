package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.models.stream.Series;

@ApplicationScoped
public class SeriesRepository extends BaseStreamRepository<Series> {

  @Override
  protected String getTableName() {
    return "series";
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

  /**
   * Batch upsert series (update if exists by external_id, insert if new) Uses ON DUPLICATE KEY
   * UPDATE for efficient batch processing
   */
  @Override
  public Uni<Void> batchUpsert(List<Series> seriesList) {
    if (seriesList == null || seriesList.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    // Build INSERT ... ON DUPLICATE KEY UPDATE statement
    String sql =
        "INSERT INTO series (source_id, external_id, num, allow_deny, name, category_id,"
            + " category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE num=VALUES(num),"
            + " allow_deny=VALUES(allow_deny), name=VALUES(name), category_id=VALUES(category_id),"
            + " category_ids=VALUES(category_ids), is_adult=VALUES(is_adult),"
            + " labels=VALUES(labels), data=VALUES(data), added_date=VALUES(added_date),"
            + " release_date=VALUES(release_date)";

    return pool.preparedQuery(sql)
        .executeBatch(
            seriesList.stream()
                .map(
                    series ->
                        Tuple.tuple()
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
                            .addLocalDate(series.getReleaseDate()))
                .collect(Collectors.toList()))
        .replaceWithVoid();
  }
}
