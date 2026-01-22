package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
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
}
