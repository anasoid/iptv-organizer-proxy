package org.anasoid.iptvorganizer.repositories.synch;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

@ApplicationScoped
public class SyncLogRepository extends BaseRepository<SyncLog> {

  @Override
  protected String getTableName() {
    return "sync_logs";
  }

  @Override
  public Long insert(SyncLog syncLog) {
    String sql =
        "INSERT INTO sync_logs (source_id, sync_type, started_at, completed_at, status, items_added, items_updated, items_deleted, error_message, duration_seconds, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, syncLog.getSourceId());
      stmt.setString(2, syncLog.getSyncType());
      stmt.setObject(3, syncLog.getStartedAt());
      stmt.setObject(4, syncLog.getCompletedAt());
      stmt.setString(5, syncLog.getStatus() != null ? syncLog.getStatus().getValue() : null);
      stmt.setInt(6, syncLog.getItemsAdded());
      stmt.setInt(7, syncLog.getItemsUpdated());
      stmt.setInt(8, syncLog.getItemsDeleted());
      stmt.setString(9, syncLog.getErrorMessage());
      stmt.setInt(10, syncLog.getDurationSeconds() != null ? syncLog.getDurationSeconds() : 0);
      stmt.setObject(
          11, syncLog.getCreatedAt() != null ? syncLog.getCreatedAt() : LocalDateTime.now());
      stmt.setObject(
          12, syncLog.getUpdatedAt() != null ? syncLog.getUpdatedAt() : LocalDateTime.now());
      stmt.executeUpdate();

      // Get generated key using standard JDBC approach - works with MySQL, H2, SQLite
      try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
        if (generatedKeys.next()) {
          Long id = generatedKeys.getLong(1);
          syncLog.setId(id);
          return id;
        }
      }
      throw new IllegalStateException("Unable to retrieve inserted ID for sync log");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert sync log", e);
    }
  }

  @Override
  public void update(SyncLog syncLog) {
    String sql =
        "UPDATE sync_logs SET source_id = ?, sync_type = ?, started_at = ?, completed_at = ?, status = ?, items_added = ?, items_updated = ?, items_deleted = ?, error_message = ?, duration_seconds = ?, updated_at = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, syncLog.getSourceId());
      stmt.setString(2, syncLog.getSyncType());
      stmt.setObject(3, syncLog.getStartedAt());
      stmt.setObject(4, syncLog.getCompletedAt());
      stmt.setString(5, syncLog.getStatus() != null ? syncLog.getStatus().getValue() : null);
      stmt.setInt(6, syncLog.getItemsAdded());
      stmt.setInt(7, syncLog.getItemsUpdated());
      stmt.setInt(8, syncLog.getItemsDeleted());
      stmt.setString(9, syncLog.getErrorMessage());
      stmt.setInt(10, syncLog.getDurationSeconds() != null ? syncLog.getDurationSeconds() : 0);
      stmt.setObject(
          11, syncLog.getUpdatedAt() != null ? syncLog.getUpdatedAt() : LocalDateTime.now());
      stmt.setLong(12, syncLog.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update sync log", e);
    }
  }

  @Override
  protected SyncLog mapRow(ResultSet rs) throws SQLException {
    return SyncLog.builder()
        .id(rs.getLong("id"))
        .sourceId(rs.getLong("source_id"))
        .syncType(rs.getString("sync_type"))
        .startedAt(rs.getObject("started_at", LocalDateTime.class))
        .completedAt(rs.getObject("completed_at", LocalDateTime.class))
        .status(SyncLogStatus.fromValue(rs.getString("status")))
        .itemsAdded(rs.getInt("items_added"))
        .itemsUpdated(rs.getInt("items_updated"))
        .itemsDeleted(rs.getInt("items_deleted"))
        .errorMessage(rs.getString("error_message"))
        .durationSeconds(rs.getInt("duration_seconds"))
        .build();
  }

  /** Find sync logs by source ID with optional sync_type and status filters */
  public List<SyncLog> findBySourceIdFiltered(
      Long sourceId, String syncType, String status, int page, int limit) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(sourceId);

    if (syncType != null && !syncType.isEmpty()) {
      whereClause.append(" AND sync_type = ?");
      params.add(syncType);
    }

    if (status != null && !status.isEmpty()) {
      whereClause.append(" AND status = ?");
      params.add(status);
    }

    return findWherePaged(whereClause.toString(), page, limit, "started_at DESC", params.toArray());
  }

  /** Count sync logs by source ID with optional filters */
  public Long countBySourceIdFiltered(Long sourceId, String syncType, String status) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(sourceId);

    if (syncType != null && !syncType.isEmpty()) {
      whereClause.append(" AND sync_type = ?");
      params.add(syncType);
    }

    if (status != null && !status.isEmpty()) {
      whereClause.append(" AND status = ?");
      params.add(status);
    }

    return countWhere(whereClause.toString(), params.toArray());
  }

  /** Find sync logs by source ID */
  public List<SyncLog> findBySourceId(Long sourceId) {
    List<SyncLog> results = new ArrayList<>();
    String sql = "SELECT * FROM sync_logs WHERE source_id = ? ORDER BY started_at DESC";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find sync logs by source id", e);
    }
    return results;
  }

  /** Find sync logs by status */
  public List<SyncLog> findByStatus(SyncLogStatus status) {
    List<SyncLog> results = new ArrayList<>();
    String sql = "SELECT * FROM sync_logs WHERE status = ? ORDER BY started_at DESC";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, status.getValue());
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find sync logs by status", e);
    }
    return results;
  }
}
