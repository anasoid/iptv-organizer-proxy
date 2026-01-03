package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Series;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SeriesRepository extends BaseRepository<Series> {

    @Override
    protected String getTableName() {
        return "series";
    }

    @Override
    public Uni<Long> insert(Series series) {
        String sql = "INSERT INTO series (source_id, stream_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(series.getSourceId())
            .addInteger(series.getStreamId())
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
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(Series series) {
        String sql = "UPDATE series SET source_id = ?, stream_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(series.getSourceId())
            .addInteger(series.getStreamId())
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
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
    }

    @Override
    protected Series mapRow(Row row) {
        return Series.builder()
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
}
