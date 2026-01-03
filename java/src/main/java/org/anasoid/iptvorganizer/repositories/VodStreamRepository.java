package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.VodStream;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VodStreamRepository extends BaseRepository<VodStream> {

    @Override
    protected String getTableName() {
        return "vod_streams";
    }

    @Override
    public Uni<Long> insert(VodStream stream) {
        String sql = "INSERT INTO vod_streams (source_id, stream_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
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
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(VodStream stream) {
        String sql = "UPDATE vod_streams SET source_id = ?, stream_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
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
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
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
}
