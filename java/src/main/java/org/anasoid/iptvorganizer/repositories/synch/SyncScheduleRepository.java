package org.anasoid.iptvorganizer.repositories.synch;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.SyncSchedule;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

@ApplicationScoped
public class SyncScheduleRepository extends BaseRepository<SyncSchedule> {

  @Override
  protected String getTableName() {
    return "sync_schedule";
  }

  @Override
  public Uni<Long> insert(SyncSchedule schedule) {
    String sql =
        "INSERT INTO sync_schedule (source_id, task_type, next_sync, last_sync, sync_interval)"
            + " VALUES (?, ?, ?, ?, ?)";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                schedule.getSourceId(),
                schedule.getTaskType(),
                schedule.getNextSync(),
                schedule.getLastSync(),
                schedule.getSyncInterval()))
        .map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(SyncSchedule schedule) {
    String sql =
        "UPDATE sync_schedule SET source_id = ?, task_type = ?, next_sync = ?, last_sync = ?,"
            + " sync_interval = ? WHERE id = ?";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                schedule.getSourceId(),
                schedule.getTaskType(),
                schedule.getNextSync(),
                schedule.getLastSync(),
                schedule.getSyncInterval(),
                schedule.getId()))
        .replaceWithVoid();
  }

  @Override
  protected SyncSchedule mapRow(Row row) {
    return SyncSchedule.builder()
        .id(row.getLong("id"))
        .sourceId(row.getLong("source_id"))
        .taskType(row.getString("task_type"))
        .nextSync(row.getLocalDateTime("next_sync"))
        .lastSync(row.getLocalDateTime("last_sync"))
        .syncInterval(row.getInteger("sync_interval"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
  }
}
