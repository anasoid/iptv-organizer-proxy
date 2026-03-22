package org.anasoid.iptvorganizer.repositories;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.AccessLevel;
import lombok.Getter;
import org.anasoid.iptvorganizer.cache.Cache;
import org.anasoid.iptvorganizer.cache.CacheManager;
import org.anasoid.iptvorganizer.models.entity.BaseEntity;

public abstract class BaseRepository<T extends BaseEntity> {

  @Getter(AccessLevel.PROTECTED)
  private Cache<T> cache;

  @Inject protected DataSource dataSource;
  @Inject protected CacheManager cacheManager;

  protected abstract String getTableName();

  protected abstract T mapRow(ResultSet rs) throws SQLException;

  public T findById(Long id) {
    Optional<T> cacheOpt = getCache().get(id);
    if (cacheOpt.isPresent()) {
      return cacheOpt.get();
    }
    String sql = "SELECT * FROM " + getTableName() + " WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, id);
      try (ResultSet rs = stmt.executeQuery()) {
        T result = rs.next() ? mapRow(rs) : null;
        getCache().put(null, id, result);
        return result;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find " + getTableName() + " by id: " + id, e);
    }
  }

  public List<T> findAll() {
    List<T> results = new ArrayList<>();
    String sql = "SELECT * FROM " + getTableName();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        results.add(mapRow(rs));
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find " + getTableName() + " all", e);
    }
    return results;
  }

  public final Long insert(T entity) {
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setCreatedAt(LocalDateTime.now());
    Long result = internalInsert(entity);
    getCache().invalidateAll();
    return result;
  }

  public final void update(T entity) {
    entity.setUpdatedAt(LocalDateTime.now());
    internalUpdate(entity);
    getCache().invalidateAll();
  }

  protected abstract Long internalInsert(T entity);

  protected abstract void internalUpdate(T entity);

  public void delete(Long id) {
    String sql = "DELETE FROM " + getTableName() + " WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, id);
      stmt.executeUpdate();
      getCache().invalidateAll();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete " + getTableName() + " by id: " + id, e);
    }
  }

  /** Get total count of records in the table */
  public Long count() {
    String sql = "SELECT COUNT(*) as cnt FROM " + getTableName();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      return rs.next() ? rs.getLong("cnt") : 0L;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count records " + getTableName(), e);
    }
  }

  /** Get paginated results */
  public List<T> findAllPaged(int page, int limit) {
    List<T> results = new ArrayList<>();
    int offset = (page - 1) * limit;
    String sql = "SELECT * FROM " + getTableName() + " LIMIT ? OFFSET ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, limit);
      stmt.setInt(2, offset);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find " + getTableName() + " paged results", e);
    }
    return results;
  }

  /** Count records matching a where clause */
  protected Long countWhere(String whereClause, Object... params) {
    String sql = "SELECT COUNT(*) as cnt FROM " + getTableName() + " WHERE " + whereClause;
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        stmt.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? rs.getLong("cnt") : 0L;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to count " + getTableName() + " with where clause", e);
    }
  }

  /** Find records with where clause and pagination */
  protected List<T> findWherePaged(
      String whereClause, int page, int limit, String orderBy, Object... params) {
    List<T> results = new ArrayList<>();
    int offset = (page - 1) * limit;
    StringBuilder sql =
        new StringBuilder("SELECT * FROM ")
            .append(getTableName())
            .append(" WHERE ")
            .append(whereClause);
    if (orderBy != null && !orderBy.isEmpty()) {
      sql.append(" ORDER BY ").append(orderBy);
    }
    sql.append(" LIMIT ? OFFSET ?");

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
      // Set where clause parameters
      for (int i = 0; i < params.length; i++) {
        stmt.setObject(i + 1, params[i]);
      }
      // Set limit and offset
      stmt.setInt(params.length + 1, limit);
      stmt.setInt(params.length + 2, offset);

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(
          "Failed to find " + getTableName() + "paged results with where clause", e);
    }
    return results;
  }

  /**
   * Get the inserted ID from a generated key. Must be called after an INSERT.
   *
   * @param stmt the PreparedStatement that was used for insertion
   * @return the generated ID
   */
  protected Long getGeneratedId(PreparedStatement stmt) throws SQLException {
    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
      if (generatedKeys.next()) {
        return generatedKeys.getLong(1);
      }
    }
    throw new IllegalStateException("Unable to retrieve inserted ID");
  }

  /**
   * Find IDs only for a specific source_id (lightweight query) Used for delete detection in sync
   * operations
   */
  public List<Long> findIdsBySourceId(Long sourceId) {
    List<Long> ids = new ArrayList<>();
    String sql = "SELECT id FROM " + getTableName() + " WHERE source_id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getLong("id"));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(
          "Failed to find " + getTableName() + " ids by source id: " + sourceId, e);
    }
    return ids;
  }

  /**
   * Safely converts a ResultSet column value to Boolean, handling SQLite's integer 0/1
   * representation as well as native Boolean types.
   *
   * @param rs the ResultSet
   * @param column the column name
   * @return Boolean value, or null if the column is SQL NULL
   */
  protected Boolean toBoolean(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue() != 0;
    }
    return Boolean.parseBoolean(value.toString());
  }

  @PostConstruct
  protected void init() {
    cache = cacheManager.getCache(getTableName(), cacheSize(), cacheDuration());
  }

  protected abstract int cacheSize();

  protected abstract Duration cacheDuration();
}
