package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.SyncLog;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SyncLogRepository extends BaseRepository<SyncLog> {

    @Override
    protected String getTableName() {
        return "sync_logs";
    }

    @Override
    public Uni<Long> insert(SyncLog syncLog) {
        String sql = "INSERT INTO sync_logs (source_id, sync_type, started_at, completed_at, status, items_added, items_updated, items_deleted, error_message, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(syncLog.getSourceId())
            .addString(syncLog.getSyncType())
            .addLocalDateTime(syncLog.getStartedAt())
            .addLocalDateTime(syncLog.getCompletedAt())
            .addString(syncLog.getStatus())
            .addInteger(syncLog.getItemsAdded())
            .addInteger(syncLog.getItemsUpdated())
            .addInteger(syncLog.getItemsDeleted())
            .addString(syncLog.getErrorMessage())
            .addInteger(syncLog.getDurationSeconds());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(SyncLog syncLog) {
        String sql = "UPDATE sync_logs SET source_id = ?, sync_type = ?, started_at = ?, completed_at = ?, status = ?, items_added = ?, items_updated = ?, items_deleted = ?, error_message = ?, duration_seconds = ? WHERE id = ?";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(syncLog.getSourceId())
            .addString(syncLog.getSyncType())
            .addLocalDateTime(syncLog.getStartedAt())
            .addLocalDateTime(syncLog.getCompletedAt())
            .addString(syncLog.getStatus())
            .addInteger(syncLog.getItemsAdded())
            .addInteger(syncLog.getItemsUpdated())
            .addInteger(syncLog.getItemsDeleted())
            .addString(syncLog.getErrorMessage())
            .addInteger(syncLog.getDurationSeconds())
            .addLong(syncLog.getId());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
    }

    @Override
    protected SyncLog mapRow(Row row) {
        return SyncLog.builder()
            .id(row.getLong("id"))
            .sourceId(row.getLong("source_id"))
            .syncType(row.getString("sync_type"))
            .startedAt(row.getLocalDateTime("started_at"))
            .completedAt(row.getLocalDateTime("completed_at"))
            .status(row.getString("status"))
            .itemsAdded(row.getInteger("items_added"))
            .itemsUpdated(row.getInteger("items_updated"))
            .itemsDeleted(row.getInteger("items_deleted"))
            .errorMessage(row.getString("error_message"))
            .durationSeconds(row.getInteger("duration_seconds"))
            .build();
    }
}
