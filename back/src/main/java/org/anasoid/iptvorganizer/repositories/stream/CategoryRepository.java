package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class CategoryRepository extends SourcedEntityRepository<Category> {

  @Override
  protected String getTableName() {
    return "categories";
  }

  @Override
  protected Long internalInsert(Category category) {
    String sql =
        "INSERT INTO categories (source_id, external_id, name, type, num, allow_deny, parent_id, labels, black_list) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, category.getSourceId());
      stmt.setInt(2, category.getExternalId());
      stmt.setString(3, category.getName());
      stmt.setString(4, category.getType());
      stmt.setObject(5, category.getNum());
      stmt.setString(
          6, category.getAllowDeny() != null ? category.getAllowDeny().getValue() : null);
      stmt.setObject(7, category.getParentId());
      stmt.setString(8, category.getLabels());
      stmt.setString(
          9,
          category.getBlackList() != null
              ? category.getBlackList().getValue()
              : Category.BlackListStatus.DEFAULT.getValue());
      stmt.executeUpdate();
      Long id = getGeneratedId(stmt);
      category.setId(id);
      return id;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert category", e);
    }
  }

  @Override
  protected void internalUpdate(Category category) {
    String sql =
        "UPDATE categories SET source_id = ?, external_id = ?, name = ?, type = ?, num = ?, allow_deny = ?, parent_id = ?, labels = ?, black_list = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, category.getSourceId());
      stmt.setInt(2, category.getExternalId());
      stmt.setString(3, category.getName());
      stmt.setString(4, category.getType());
      stmt.setInt(5, category.getNum());
      stmt.setString(
          6, category.getAllowDeny() != null ? category.getAllowDeny().getValue() : null);
      stmt.setObject(7, category.getParentId());
      stmt.setString(8, category.getLabels());
      stmt.setString(
          9,
          category.getBlackList() != null
              ? category.getBlackList().getValue()
              : Category.BlackListStatus.DEFAULT.getValue());
      stmt.setLong(10, category.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update category", e);
    }
  }

  @Override
  protected Category mapRow(ResultSet rs) throws SQLException {
    return Category.builder()
        .id(rs.getLong("id"))
        .sourceId(rs.getLong("source_id"))
        .externalId(rs.getInt("external_id"))
        .name(rs.getString("name"))
        .type(rs.getString("type"))
        .num(rs.getInt("num"))
        .allowDeny(BaseStream.AllowDenyStatus.fromValue(rs.getString("allow_deny")))
        .parentId((Integer) rs.getObject("parent_id"))
        .labels(rs.getString("labels"))
        .blackList(Category.BlackListStatus.fromValue(rs.getString("black_list")))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
  }

  /** Find categories by source ID with optional category type filter and search */
  public List<Category> findBySourceIdFiltered(
      Long sourceId, String categoryType, String search, int page, int limit) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(sourceId);

    if (categoryType != null && !categoryType.isEmpty()) {
      whereClause.append(" AND type = ?");
      params.add(categoryType);
    }

    if (search != null && !search.isEmpty()) {
      whereClause.append(" AND name LIKE ?");
      params.add("%" + search + "%");
    }

    return findWherePaged(whereClause.toString(), page, limit, "id DESC", params.toArray());
  }

  /** Count categories by source ID with optional filters */
  public Long countBySourceIdFiltered(Long sourceId, String categoryType, String search) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(sourceId);

    if (categoryType != null && !categoryType.isEmpty()) {
      whereClause.append(" AND type = ?");
      params.add(categoryType);
    }

    if (search != null && !search.isEmpty()) {
      whereClause.append(" AND name LIKE ?");
      params.add("%" + search + "%");
    }

    return countWhere(whereClause.toString(), params.toArray());
  }

  /** Find category by source, external_id, and type */
  public Category findBySourceCategoryType(Long sourceId, Integer categoryId, String categoryType) {

    String key = sourceId + "/" + categoryType + "/" + categoryId;
    Optional<Category> catOpt = getCache().get(key);
    if (catOpt.isPresent()) {
      return catOpt.get();
    }
    String sql =
        "SELECT * FROM categories WHERE source_id = ? AND external_id = ? AND type = ? LIMIT 1";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setInt(2, categoryId);
      stmt.setString(3, categoryType);
      try (ResultSet rs = stmt.executeQuery()) {
        Category result = rs.next() ? mapRow(rs) : null;
        if (result != null) {
          getCache().put(key, null, result);
        } else {
          getCache().put(key, result.getId(), result);
        }
        return result;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find by source category type", e);
    }
  }

  /**
   * Get or create "Unknown" category for a source and type Used when streams have no category
   * assigned
   *
   * @param sourceId Source ID
   * @param categoryType Category type (live, vod, series)
   * @return Database ID of the Unknown category
   */
  public Integer getOrCreateUnknownCategory(Long sourceId, String categoryType) {
    Category existing = findBySourceCategoryType(sourceId, 0, categoryType);
    if (existing != null) {
      return existing.getExternalId();
    }

    // Create new Unknown category
    Category unknownCategory = new Category();
    unknownCategory.setSourceId(sourceId);
    unknownCategory.setExternalId(0);
    unknownCategory.setType(categoryType);
    unknownCategory.setName("Unknown");
    unknownCategory.setNum(0);
    unknownCategory.setParentId(null);
    unknownCategory.setLabels("unknown");

    insert(unknownCategory);
    return 0;
  }

  /** Find external IDs by source ID and type */
  public List<Integer> findExternalIdsBySourceIdAndType(Long sourceId, StreamType type) {
    List<Integer> ids = new ArrayList<>();
    String sql = "SELECT external_id FROM " + getTableName() + " WHERE source_id = ? AND type = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setString(2, type.getCategoryType());
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getInt("external_id"));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find external ids by source id and type", e);
    }
    return ids;
  }

  public Category findByExternalIdAndType(Integer externalId, Long sourceId, StreamType type) {
    String sql =
        "SELECT * FROM " + getTableName() + " WHERE external_id = ? AND source_id = ? AND type = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, externalId);
      stmt.setLong(2, sourceId);
      stmt.setString(3, type.getCategoryType());
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? mapRow(rs) : null;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find by external id and type", e);
    }
  }

  public void deleteByExternalIdAndType(Integer externalId, Long sourceId, StreamType type) {
    String sql =
        "DELETE FROM " + getTableName() + " WHERE external_id = ? AND source_id = ? AND type = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setInt(1, externalId);
      stmt.setLong(2, sourceId);
      stmt.setString(3, type.getCategoryType());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to delete by external id and type", e);
    }
  }

  /**
   * Check if Category has any functional field changes compared to existing. Compares base
   * SourcedEntity fields plus Category-specific fields (name, type, allowDeny, parentId, labels).
   *
   * @param newEntity Category with new data
   * @param existingEntity Category from database
   * @return true if any functional field has changed, false otherwise
   */
  @Override
  public boolean hasFunctionalChanges(Category newEntity, Category existingEntity) {
    // First check base SourcedEntity fields
    if (super.hasFunctionalChanges(newEntity, existingEntity)) {
      return true;
    }

    // Check Category-specific fields
    if (!Objects.equals(newEntity.getName(), existingEntity.getName())) return true;
    if (!Objects.equals(newEntity.getType(), existingEntity.getType())) return true;
    if (!Objects.equals(newEntity.getAllowDeny(), existingEntity.getAllowDeny())) return true;
    if (!Objects.equals(newEntity.getParentId(), existingEntity.getParentId())) return true;
    if (!Objects.equals(newEntity.getLabels(), existingEntity.getLabels())) return true;

    // For blackList: Only update if existing is not a FORCE_* override
    if (existingEntity.getBlackList() != Category.BlackListStatus.FORCE_HIDE
        && existingEntity.getBlackList() != Category.BlackListStatus.FORCE_VISIBLE) {
      if (!Objects.equals(newEntity.getBlackList(), existingEntity.getBlackList())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Find all categories for a source where blackList status indicates hiding. Returns categories
   * with HIDE or FORCE_HIDE status.
   *
   * @param sourceId Source ID
   * @param type Category type (live, vod, series)
   * @return List of blacklisted categories
   */
  public List<Category> findBlacklistedBySourceAndType(Long sourceId, String type) {
    List<Category> results = new ArrayList<>();
    String sql =
        "SELECT * FROM categories WHERE source_id = ? AND type = ? AND (black_list = 'hide' OR black_list = 'force_hide')";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, sourceId);
      stmt.setString(2, type);
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find blacklisted categories", e);
    }
    return results;
  }

  /**
   * Build a map of all categories for a source by external ID. Used for efficient category lookups
   * during stream import.
   *
   * @param sourceId Source ID
   * @param type Category type (live, vod, series)
   * @return Map of externalId -> Category
   */
  public Map<Integer, Category> findBySourceAndTypeAsMap(Long sourceId, String type) {
    Map<Integer, Category> categoryMap = new HashMap<>();
    List<Category> categories = findBySourceIdFiltered(sourceId, type, null, 1, Integer.MAX_VALUE);
    for (Category category : categories) {
      categoryMap.put(category.getExternalId(), category);
    }
    return categoryMap;
  }

  @Override
  protected int cacheSize() {
    return 10;
  }

  @Override
  protected Duration cacheDuration() {
    return Duration.ofHours(1);
  }
}
