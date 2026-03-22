package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;

/**
 * Base repository for stream-like entities (LiveStream, VodStream, Series).
 *
 * @param <T> The stream type extending BaseStream
 */
public abstract class BaseStreamRepository<T extends BaseStream> extends SourcedEntityRepository<T>
    implements SynchronizedItemRepository<T> {

  @Override
  @Transactional
  public int insertOrUpdateByExternalId(List<T> entities) {
    return internalInsertOrUpdateByExternalId(entities);
  }

  /**
   * Find streams by source ID and category ID with limit. Used for lazy loading and filtering.
   *
   * @param sourceId The source ID
   * @param categoryId The category ID (external_id)
   * @param limit Maximum number of streams to return
   * @return List of streams for the category
   */
  public List<T> findBySourceAndCategory(Long sourceId, Integer categoryId, int limit) {
    List<T> results = new ArrayList<>();
    String sql =
        "SELECT * FROM "
            + getTableName()
            + " WHERE source_id = ? AND category_id = ? ORDER BY num ASC, id DESC LIMIT ?";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setInt(2, categoryId);
      stmt.setInt(3, limit);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to find by source and category in " + getTableName(), e);
    }
    return results;
  }

  /**
   * Stream entities by source ID using lazy iterator for O(1) memory usage.
   *
   * <p>Returns a JdbcStreamIterator that fetches rows one at a time from the database instead of
   * loading all into memory. This is the streaming equivalent of findBySourceId().
   *
   * @param sourceId The source ID to filter by
   * @return Iterator that streams rows from the database
   */
  public Iterator<T> streamBySourceId(Long sourceId) {
    return super.streamBySourceId(sourceId);
  }

  /**
   * Find stream by source ID and external stream ID.
   *
   * @param sourceId The source ID
   * @param externalId The external stream ID
   * @return The stream or null if not found
   */
  public T findBySourceAndStreamId(Long sourceId, Integer externalId) {
    return findByExternalId(externalId, sourceId);
  }

  /**
   * Check if BaseStream has any functional field changes compared to existing. Compares base
   * SourcedEntity fields plus BaseStream-specific fields (categoryId, allowDeny, name, categoryIds,
   * isAdult, labels, data, addedDate, releaseDate).
   *
   * @param newEntity BaseStream with new data
   * @param existingEntity BaseStream from database
   * @return true if any functional field has changed, false otherwise
   */
  @Override
  public boolean hasFunctionalChanges(T newEntity, T existingEntity) {
    // First check base SourcedEntity fields
    if (super.hasFunctionalChanges(newEntity, existingEntity)) {
      return true;
    }

    // Check BaseStream-specific fields
    if (!Objects.equals(newEntity.getCategoryId(), existingEntity.getCategoryId())) return true;
    if (!Objects.equals(newEntity.getAllowDeny(), existingEntity.getAllowDeny())) return true;
    if (!Objects.equals(newEntity.getName(), existingEntity.getName())) return true;
    if (!Objects.equals(newEntity.getCategoryIds(), existingEntity.getCategoryIds())) return true;
    if (!Objects.equals(newEntity.getIsAdult(), existingEntity.getIsAdult())) return true;
    if (!Objects.equals(newEntity.getLabels(), existingEntity.getLabels())) return true;
    if (!Objects.equals(newEntity.getData(), existingEntity.getData())) return true;
    if (!Objects.equals(newEntity.getAddedDate(), existingEntity.getAddedDate())) return true;
    if (!Objects.equals(newEntity.getReleaseDate(), existingEntity.getReleaseDate())) return true;

    return false;
  }

  /**
   * Delete all streams for a specific category. Used during blacklist cleanup.
   *
   * @param sourceId Source ID
   * @param categoryId Category external ID
   * @return Number of streams deleted
   */
  public int deleteByCategory(Long sourceId, Integer categoryId) {
    String sql = "DELETE FROM " + getTableName() + " WHERE source_id = ? AND category_id = ?";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setInt(2, categoryId);
      return stmt.executeUpdate();
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to delete by category in " + getTableName(), e);
    }
  }

  /**
   * Count streams for a specific category. Used to check if streams exist before deletion.
   *
   * @param sourceId Source ID
   * @param categoryId Category external ID
   * @return Number of streams in category
   */
  public long countByCategory(Long sourceId, Integer categoryId) {
    String sql =
        "SELECT COUNT(*) FROM " + getTableName() + " WHERE source_id = ? AND category_id = ?";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setInt(2, categoryId);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to count by category in " + getTableName(), e);
    }
    return 0;
  }

  /**
   * Find streams by source ID with pagination. Used for listing streams with pagination support.
   *
   * @param sourceId The source ID
   * @param page The page number (1-indexed)
   * @param limit The number of results per page
   * @return List of streams for the page
   */
  public List<T> findBySourceIdPaged(Long sourceId, int page, int limit) {
    List<T> results = new ArrayList<>();
    int offset = (page - 1) * limit;
    String sql =
        "SELECT * FROM "
            + getTableName()
            + " WHERE source_id = ? ORDER BY num ASC, id DESC LIMIT ? OFFSET ?";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setInt(2, limit);
      stmt.setInt(3, offset);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(
          "Failed to find by source ID with pagination in " + getTableName(), e);
    }
    return results;
  }

  /**
   * Count all streams for a source ID.
   *
   * @param sourceId The source ID
   * @return Total count of streams
   */
  public long countBySourceId(Long sourceId) {
    String sql = "SELECT COUNT(*) FROM " + getTableName() + " WHERE source_id = ?";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to count by source ID in " + getTableName(), e);
    }
    return 0;
  }

  /**
   * Find streams by source ID with pagination and case-insensitive search on name only.
   *
   * @param sourceId The source ID
   * @param search The search term (case-insensitive, matches name only)
   * @param page The page number (1-indexed)
   * @param limit The number of results per page
   * @return List of streams for the page matching the search
   */
  public List<T> findBySourceIdPagedWithSearch(Long sourceId, String search, int page, int limit) {
    List<T> results = new ArrayList<>();
    int offset = (page - 1) * limit;
    String sql =
        "SELECT * FROM "
            + getTableName()
            + " WHERE source_id = ? AND LOWER(name) LIKE ? "
            + "ORDER BY num ASC, id DESC LIMIT ? OFFSET ?";
    String searchTerm = "%" + search.toLowerCase() + "%";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setString(2, searchTerm);
      stmt.setInt(3, limit);
      stmt.setInt(4, offset);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(
          "Failed to find by source ID with pagination and search in " + getTableName(), e);
    }
    return results;
  }

  /**
   * Count all streams for a source ID matching a case-insensitive search on name only.
   *
   * @param sourceId The source ID
   * @param search The search term (case-insensitive, matches name only)
   * @return Total count of streams matching the search
   */
  public long countBySourceIdWithSearch(Long sourceId, String search) {
    String sql =
        "SELECT COUNT(*) FROM " + getTableName() + " WHERE source_id = ? AND LOWER(name) LIKE ?";
    String searchTerm = "%" + search.toLowerCase() + "%";
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setString(2, searchTerm);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(
          "Failed to count by source ID with search in " + getTableName(), e);
    }
    return 0;
  }

  @Override
  protected int cacheSize() {
    return 5;
  }

  @Override
  protected Duration cacheDuration() {
    return Duration.ofHours(1);
  }
}
