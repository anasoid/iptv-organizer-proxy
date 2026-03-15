package org.anasoid.iptvorganizer.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.models.entity.ConnectionLog;

@ApplicationScoped
public class ConnectionLogRepository extends BaseRepository<ConnectionLog> {

  @Override
  protected String getTableName() {
    return "connection_logs";
  }

  @Override
  protected Long internalInsert(ConnectionLog log) {
    String sql =
        "INSERT INTO connection_logs (client_id, action, ip_address, user_agent) VALUES (?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, log.getClientId());
      stmt.setString(2, log.getAction());
      stmt.setString(3, log.getIpAddress());
      stmt.setString(4, log.getUserAgent());
      stmt.executeUpdate();
      return getGeneratedId(stmt);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert connection log", e);
    }
  }

  @Override
  protected void internalUpdate(ConnectionLog log) {
    String sql =
        "UPDATE connection_logs SET client_id = ?, action = ?, ip_address = ?, user_agent = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, log.getClientId());
      stmt.setString(2, log.getAction());
      stmt.setString(3, log.getIpAddress());
      stmt.setString(4, log.getUserAgent());
      stmt.setLong(5, log.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update connection log", e);
    }
  }

  @Override
  protected ConnectionLog mapRow(ResultSet rs) throws SQLException {
    return ConnectionLog.builder()
        .id(rs.getLong("id"))
        .clientId(rs.getLong("client_id"))
        .action(rs.getString("action"))
        .ipAddress(rs.getString("ip_address"))
        .userAgent(rs.getString("user_agent"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .build();
  }

  @Override
  protected int cacheSize() {
    return 0;
  }

  @Override
  protected Duration cacheDuration() {
    return Duration.ofHours(0);
  }
}
