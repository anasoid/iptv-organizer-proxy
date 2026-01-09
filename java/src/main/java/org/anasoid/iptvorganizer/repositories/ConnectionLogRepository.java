package org.anasoid.iptvorganizer.repositories;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.ConnectionLog;

@ApplicationScoped
public class ConnectionLogRepository extends BaseRepository<ConnectionLog> {

  @Override
  protected String getTableName() {
    return "connection_logs";
  }

  @Override
  public Uni<Long> insert(ConnectionLog log) {
    String sql =
        "INSERT INTO connection_logs (client_id, action, ip_address, user_agent) VALUES (?, ?, ?, ?)";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(log.getClientId(), log.getAction(), log.getIpAddress(), log.getUserAgent()))
        .map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(ConnectionLog log) {
    String sql =
        "UPDATE connection_logs SET client_id = ?, action = ?, ip_address = ?, user_agent = ? WHERE id = ?";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                log.getClientId(),
                log.getAction(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getId()))
        .replaceWithVoid();
  }

  @Override
  protected ConnectionLog mapRow(Row row) {
    return ConnectionLog.builder()
        .id(row.getLong("id"))
        .clientId(row.getLong("client_id"))
        .action(row.getString("action"))
        .ipAddress(row.getString("ip_address"))
        .userAgent(row.getString("user_agent"))
        .createdAt(row.getLocalDateTime("created_at"))
        .build();
  }
}
