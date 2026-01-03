package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.SyncLog;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
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
            .createdAt(row.getLocalDateTime("created_at"))
            .updatedAt(row.getLocalDateTime("updated_at"))
            .build();
    }

    /**
     * Find sync logs by source ID with optional sync_type and status filters
     */
    public Multi<SyncLog> findBySourceIdFiltered(Long sourceId, String syncType, String status, int page, int limit) {
        StringBuilder whereClause = new StringBuilder("source_id = ?");
        Tuple params = Tuple.of(sourceId);

        if (syncType != null && !syncType.isEmpty()) {
            whereClause.append(" AND sync_type = ?");
            params = params.addString(syncType);
        }

        if (status != null && !status.isEmpty()) {
            whereClause.append(" AND status = ?");
            params = params.addString(status);
        }

        return findWherePaged(whereClause.toString(), params, page, limit, "started_at DESC");
    }

    /**
     * Count sync logs by source ID with optional filters
     */
    public Uni<Long> countBySourceIdFiltered(Long sourceId, String syncType, String status) {
        StringBuilder whereClause = new StringBuilder("source_id = ?");
        Tuple params = Tuple.of(sourceId);

        if (syncType != null && !syncType.isEmpty()) {
            whereClause.append(" AND sync_type = ?");
            params = params.addString(syncType);
        }

        if (status != null && !status.isEmpty()) {
            whereClause.append(" AND status = ?");
            params = params.addString(status);
        }

        return countWhere(whereClause.toString(), params);
    }

    /**
     * Find sync logs by source ID
     */
    public Multi<SyncLog> findBySourceId(Long sourceId) {
        return pool.preparedQuery("SELECT * FROM sync_logs WHERE source_id = ? ORDER BY started_at DESC")
            .execute(Tuple.of(sourceId))
            .onItem()
            .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .map(this::mapRow);
    }
}
