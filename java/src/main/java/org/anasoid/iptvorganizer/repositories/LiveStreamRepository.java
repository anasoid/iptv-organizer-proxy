package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.LiveStream;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LiveStreamRepository extends BaseRepository<LiveStream> {

    @Override
    protected String getTableName() {
        return "live_streams";
    }

    @Override
    public Uni<Long> insert(LiveStream stream) {
        String sql = "INSERT INTO live_streams (source_id, stream_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
    public Uni<Void> update(LiveStream stream) {
        String sql = "UPDATE live_streams SET source_id = ?, stream_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
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
    protected LiveStream mapRow(Row row) {
        return LiveStream.builder()
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
     * Batch upsert live streams using INSERT ... ON DUPLICATE KEY UPDATE
     * Efficiently inserts or updates multiple streams in a single operation
     */
    public Uni<Void> batchUpsert(List<LiveStream> streams) {
        String baseSql = "INSERT INTO live_streams (source_id, stream_id, num, allow_deny, name, " +
                         "category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES ";

        String updateClause = " ON DUPLICATE KEY UPDATE " +
                             "num = VALUES(num), " +
                             "allow_deny = VALUES(allow_deny), " +
                             "name = VALUES(name), " +
                             "category_id = VALUES(category_id), " +
                             "category_ids = VALUES(category_ids), " +
                             "is_adult = VALUES(is_adult), " +
                             "labels = VALUES(labels), " +
                             "data = VALUES(data), " +
                             "added_date = VALUES(added_date), " +
                             "release_date = VALUES(release_date), " +
                             "updated_at = CURRENT_TIMESTAMP";

        return executeBatchUpsert(streams, stream -> Tuple.tuple()
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
            .addLocalDate(stream.getReleaseDate()),
            baseSql, updateClause
        );
    }

    /**
     * Get all stream IDs for a given source
     * Used to determine which streams exist and which need to be deleted
     */
    public Uni<Set<Integer>> getStreamIdsBySourceId(Long sourceId) {
        String sql = "SELECT stream_id FROM live_streams WHERE source_id = ?";
        return pool.preparedQuery(sql)
            .execute(Tuple.of(sourceId))
            .map(rowSet -> {
                Set<Integer> streamIds = new HashSet<>();
                rowSet.forEach(row -> streamIds.add(row.getInteger("stream_id")));
                return streamIds;
            });
    }

    /**
     * Delete all streams for a source that are NOT in the keepIds set
     * Used to remove obsolete streams after sync
     */
    public Uni<Integer> deleteStreamsNotInSet(Long sourceId, Set<Integer> keepIds) {
        if (keepIds.isEmpty()) {
            // Delete all streams for this source
            String sql = "DELETE FROM live_streams WHERE source_id = ?";
            return pool.preparedQuery(sql)
                .execute(Tuple.of(sourceId))
                .map(rowSet -> rowSet.rowCount());
        }

        // Delete streams not in keepIds
        StringBuilder sql = new StringBuilder("DELETE FROM live_streams WHERE source_id = ? AND stream_id NOT IN (");
        Tuple params = Tuple.of(sourceId);

        int i = 0;
        for (Integer streamId : keepIds) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            params.addInteger(streamId);
            i++;
        }
        sql.append(")");

        return pool.preparedQuery(sql.toString())
            .execute(params)
            .map(rowSet -> rowSet.rowCount());
    }
}
