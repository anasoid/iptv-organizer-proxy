package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;

/**
 * Base repository for stream-like entities (LiveStream, VodStream, Series).
 *
 * @param <T> The stream type extending BaseStream
 */
public abstract class BaseStreamRepository<T extends BaseStream> extends SourcedEntityRepository<T>
    implements SynchronizedItemRepository<T> {

  public record StreamQueryOptions(
      Integer categoryId,
      String search,
      Integer streamId,
      String sortBy,
      String sortDir,
      LocalDate addedDateFrom,
      LocalDate addedDateTo,
      LocalDate createdDateFrom,
      LocalDate createdDateTo,
      LocalDate updateDateFrom,
      LocalDate updateDateTo,
      LocalDate releaseDateFrom,
      LocalDate releaseDateTo,
      Double ratingMin,
      Double ratingMax,
      Long tmdb) {

    public static StreamQueryOptions empty() {
      return new StreamQueryOptions(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }
  }

  private static final Map<String, String> SORT_FIELD_MAP =
      Map.ofEntries(
          Map.entry("addedDate", "added_date"),
          Map.entry("createdAt", "created_at"),
          Map.entry("updatedAt", "updated_at"),
          Map.entry("releaseDate", "release_date"),
          Map.entry("rating", "rating"),
          Map.entry("tmdb", "tmdb"));

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
    return findBySourceIdPagedWithFilters(sourceId, StreamQueryOptions.empty(), page, limit);
  }

  /**
   * Count all streams for a source ID.
   *
   * @param sourceId The source ID
   * @return Total count of streams
   */
  public long countBySourceId(Long sourceId) {
    return countBySourceIdWithFilters(sourceId, StreamQueryOptions.empty());
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
    StreamQueryOptions options =
        new StreamQueryOptions(
            null,
            search,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return findBySourceIdPagedWithFilters(sourceId, options, page, limit);
  }

  /**
   * Count all streams for a source ID matching a case-insensitive search on name only.
   *
   * @param sourceId The source ID
   * @param search The search term (case-insensitive, matches name only)
   * @return Total count of streams matching the search
   */
  public long countBySourceIdWithSearch(Long sourceId, String search) {
    StreamQueryOptions options =
        new StreamQueryOptions(
            null,
            search,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    return countBySourceIdWithFilters(sourceId, options);
  }

  public List<T> findBySourceIdPagedWithFilters(
      Long sourceId, StreamQueryOptions options, int page, int limit) {
    List<T> results = new ArrayList<>();
    int offset = (page - 1) * limit;
    QueryParts queryParts = buildWhereClause(sourceId, options);
    String sql =
        "SELECT * FROM "
            + getTableName()
            + queryParts.whereSql
            + " ORDER BY "
            + buildOrderBy(options)
            + " LIMIT ? OFFSET ?";

    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      int parameterIndex = bindParameters(stmt, queryParts.parameters);
      stmt.setInt(parameterIndex++, limit);
      stmt.setInt(parameterIndex, offset);

      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(
          "Failed to find by source ID with pagination and filters in " + getTableName(), e);
    }
    return results;
  }

  public long countBySourceIdWithFilters(Long sourceId, StreamQueryOptions options) {
    QueryParts queryParts = buildWhereClause(sourceId, options);
    String sql = "SELECT COUNT(*) FROM " + getTableName() + queryParts.whereSql;
    try (java.sql.Connection conn = dataSource.getConnection();
        java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
      bindParameters(stmt, queryParts.parameters);
      try (java.sql.ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(
          "Failed to count by source ID with filters in " + getTableName(), e);
    }
    return 0;
  }

  private QueryParts buildWhereClause(Long sourceId, StreamQueryOptions options) {
    StreamQueryOptions effective = options != null ? options : StreamQueryOptions.empty();
    StringBuilder whereClause = new StringBuilder(" WHERE source_id = ?");
    List<Object> parameters = new ArrayList<>();
    parameters.add(sourceId);

    if (effective.categoryId() != null) {
      whereClause.append(" AND category_id = ?");
      parameters.add(effective.categoryId());
    }

    if (effective.search() != null && !effective.search().isBlank()) {
      whereClause.append(" AND LOWER(name) LIKE ?");
      parameters.add("%" + effective.search().toLowerCase(Locale.ROOT) + "%");
    }

    if (effective.streamId() != null) {
      whereClause.append(" AND external_id = ?");
      parameters.add(effective.streamId());
    }

    if (effective.addedDateFrom() != null) {
      whereClause.append(" AND added_date >= ?");
      parameters.add(effective.addedDateFrom().toString());
    }
    if (effective.addedDateTo() != null) {
      whereClause.append(" AND added_date <= ?");
      parameters.add(effective.addedDateTo().toString());
    }

    if (effective.createdDateFrom() != null) {
      whereClause.append(" AND DATE(created_at) >= ?");
      parameters.add(effective.createdDateFrom().toString());
    }
    if (effective.createdDateTo() != null) {
      whereClause.append(" AND DATE(created_at) <= ?");
      parameters.add(effective.createdDateTo().toString());
    }

    if (effective.updateDateFrom() != null) {
      whereClause.append(" AND DATE(updated_at) >= ?");
      parameters.add(effective.updateDateFrom().toString());
    }
    if (effective.updateDateTo() != null) {
      whereClause.append(" AND DATE(updated_at) <= ?");
      parameters.add(effective.updateDateTo().toString());
    }

    if (effective.releaseDateFrom() != null) {
      whereClause.append(" AND release_date >= ?");
      parameters.add(effective.releaseDateFrom().toString());
    }
    if (effective.releaseDateTo() != null) {
      whereClause.append(" AND release_date <= ?");
      parameters.add(effective.releaseDateTo().toString());
    }

    if (effective.ratingMin() != null) {
      whereClause.append(" AND rating >= ?");
      parameters.add(effective.ratingMin());
    }

    if (effective.tmdb() != null) {
      whereClause.append(" AND tmdb = ?");
      parameters.add(effective.tmdb());
    }
    if (effective.ratingMax() != null) {
      whereClause.append(" AND rating <= ?");
      parameters.add(effective.ratingMax());
    }


    return new QueryParts(whereClause.toString(), parameters);
  }

  private String buildOrderBy(StreamQueryOptions options) {
    if (options == null || options.sortBy() == null || options.sortBy().isBlank()) {
      return "num ASC, id DESC";
    }
    String column = SORT_FIELD_MAP.get(options.sortBy());
    if (column == null) {
      return "num ASC, id DESC";
    }
    String direction =
        "asc".equalsIgnoreCase(options.sortDir()) || "desc".equalsIgnoreCase(options.sortDir())
            ? options.sortDir().toUpperCase(Locale.ROOT)
            : "DESC";
    return column + " " + direction + ", id DESC";
  }

  private int bindParameters(java.sql.PreparedStatement stmt, List<Object> parameters)
      throws java.sql.SQLException {
    int parameterIndex = 1;
    for (Object parameter : parameters) {
      stmt.setObject(parameterIndex++, parameter);
    }
    return parameterIndex;
  }

  private record QueryParts(String whereSql, List<Object> parameters) {}

  @Override
  protected int cacheSize() {
    return 5;
  }

  @Override
  protected Duration cacheDuration() {
    return Duration.ofHours(1);
  }
  /**
   * Safely extract a Long value from a ResultSet column, handling nulls and type variations.
   */
  protected Long getLongSafe(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    Object value = rs.getObject(column);
    if (value == null) return null;
    if (value instanceof Long) return (Long) value;
    if (value instanceof Integer) return ((Integer) value).longValue();
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.parseLong(value.toString());
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Safely set a LocalDate parameter on a PreparedStatement. Stores as ISO-8601 string
   * "YYYY-MM-DD" to ensure compatibility across all JDBC drivers (SQLite, MySQL, H2). Using
   * setObject(LocalDate) is unreliable with SQLite JDBC which may store in datetime format
   * "YYYY-MM-DD HH:mm:ss.SSS", making it impossible to read back as LocalDate.
   */
  protected void setLocalDate(java.sql.PreparedStatement stmt, int index, java.time.LocalDate date)
      throws java.sql.SQLException {
    if (date != null) {
      stmt.setString(index, date.toString()); // ISO-8601: "YYYY-MM-DD"
    } else {
      stmt.setNull(index, java.sql.Types.DATE);
    }
  }

  /**
   * Safely read a LocalDate from a ResultSet column. Handles both "YYYY-MM-DD" and
   * "YYYY-MM-DD HH:mm:ss.SSS" formats that SQLite JDBC may produce.
   */
  protected java.time.LocalDate getLocalDate(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    String value = rs.getString(column);
    if (value == null || value.isEmpty()) return null;
    try {
      // Trim to first 10 chars to strip any time component (e.g. "2023-01-01 00:00:00.000")
      String datePart = value.length() > 10 ? value.substring(0, 10) : value;
      return java.time.LocalDate.parse(datePart);
    } catch (Exception e) {
      return null;
    }
  }
}
