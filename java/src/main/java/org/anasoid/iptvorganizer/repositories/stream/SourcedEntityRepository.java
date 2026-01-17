package org.anasoid.iptvorganizer.repositories.stream;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

/**
 * Base repository for entities that belong to a source and have ordering.
 *
 * @param <T> The entity type extending SourcedEntity
 */
public abstract class SourcedEntityRepository<T extends SourcedEntity> extends BaseRepository<T> {

  /** Find entities by source ID */
  public List<T> findBySourceId(Long sourceId) {
    List<T> results = new ArrayList<>();
    String sql =
        "SELECT * FROM " + getTableName() + " WHERE source_id = ? ORDER BY num ASC, id DESC";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find by source id", e);
    }
    return results;
  }

  /** Find external IDs by source ID */
  public List<Integer> findExternalIdsBySourceId(Long sourceId) {
    List<Integer> ids = new ArrayList<>();
    String sql = "SELECT external_id FROM " + getTableName() + " WHERE source_id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getInt("external_id"));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find external ids by source id", e);
    }
    return ids;
  }

  public T findByExternalId(Integer externalId, Long sourceId) {
    String sql = "SELECT * FROM " + getTableName() + " WHERE external_id = ? AND source_id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, externalId);
      stmt.setLong(2, sourceId);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? mapRow(rs) : null;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find by external id", e);
    }
  }

  /**
   * Find entities by external IDs in bulk using IN clause. Much more efficient than individual
   * queries for batch operations.
   *
   * @param externalIds List of external IDs to find
   * @param sourceId Source ID to filter by
   * @return Map of external_id -> entity for quick lookup
   */
  public Map<Integer, T> findByExternalIds(List<Integer> externalIds, Long sourceId) {
    if (externalIds.isEmpty()) {
      return Map.of();
    }

    Map<Integer, T> results = new HashMap<>();

    // Build IN clause with placeholders
    String placeholders = String.join(",", Collections.nCopies(externalIds.size(), "?"));
    String sql =
        "SELECT * FROM "
            + getTableName()
            + " WHERE source_id = ? AND external_id IN ("
            + placeholders
            + ")";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);

      // Bind external IDs
      for (int i = 0; i < externalIds.size(); i++) {
        stmt.setInt(i + 2, externalIds.get(i));
      }

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          T entity = mapRow(rs);
          results.put(entity.getExternalId(), entity);
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to bulk find by external ids", e);
    }

    return results;
  }

  public void deleteByExternalId(Integer externalId, Long sourceId) {
    String sql = "DELETE FROM " + getTableName() + " WHERE external_id = ? AND source_id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, externalId);
      stmt.setLong(2, sourceId);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete by external id", e);
    }
  }

  /** Count entities by source ID */
  public Long countBySourceId(Long sourceId) {
    return countWhere("source_id = ?", sourceId);
  }
}
