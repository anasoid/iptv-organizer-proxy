package org.anasoid.iptvorganizer.repositories.stream;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public class CategoryRepository extends SourcedEntityRepository<Category> {

  @Override
  protected String getTableName() {
    return "categories";
  }

  @Override
  public Uni<Long> insert(Category category) {
    String sql =
        "INSERT INTO categories (source_id, external_id, name, type, num,"
            + " allow_deny, parent_id, labels) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(category.getSourceId())
            .addInteger(category.getExternalId())
            .addString(category.getName())
            .addString(category.getType())
            .addInteger(category.getNum())
            .addString(category.getAllowDeny())
            .addInteger(category.getParentId())
            .addString(category.getLabels());
    return pool.preparedQuery(sql).execute(tuple).map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(Category category) {
    String sql =
        "UPDATE categories SET source_id = ?, external_id = ?, name = ?, type ="
            + " ?, num = ?, allow_deny = ?, parent_id = ?, labels = ? WHERE id = ?";
    io.vertx.mutiny.sqlclient.Tuple tuple =
        io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(category.getSourceId())
            .addInteger(category.getExternalId())
            .addString(category.getName())
            .addString(category.getType())
            .addInteger(category.getNum())
            .addString(category.getAllowDeny())
            .addInteger(category.getParentId())
            .addString(category.getLabels())
            .addLong(category.getId());
    return pool.preparedQuery(sql).execute(tuple).replaceWithVoid();
  }

  @Override
  protected Category mapRow(Row row) {
    return Category.builder()
        .id(row.getLong("id"))
        .sourceId(row.getLong("source_id"))
        .externalId(row.getInteger("external_id"))
        .name(row.getString("name"))
        .type(row.getString("type"))
        .num(row.getInteger("num"))
        .allowDeny(row.getString("allow_deny"))
        .parentId(row.getInteger("parent_id"))
        .labels(row.getString("labels"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
  }

  /** Find categories by source ID with optional category type filter and search */
  public Multi<Category> findBySourceIdFiltered(
      Long sourceId, String categoryType, String search, int page, int limit) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    Tuple params = Tuple.of(sourceId);

    if (categoryType != null && !categoryType.isEmpty()) {
      whereClause.append(" AND type = ?");
      params = params.addString(categoryType);
    }

    if (search != null && !search.isEmpty()) {
      whereClause.append(" AND name LIKE ?");
      params = params.addString("%" + search + "%");
    }

    return findWherePaged(whereClause.toString(), params, page, limit, "id DESC");
  }

  /** Count categories by source ID with optional filters */
  public Uni<Long> countBySourceIdFiltered(Long sourceId, String categoryType, String search) {
    StringBuilder whereClause = new StringBuilder("source_id = ?");
    Tuple params = Tuple.of(sourceId);

    if (categoryType != null && !categoryType.isEmpty()) {
      whereClause.append(" AND type = ?");
      params = params.addString(categoryType);
    }

    if (search != null && !search.isEmpty()) {
      whereClause.append(" AND name LIKE ?");
      params = params.addString("%" + search + "%");
    }

    return countWhere(whereClause.toString(), params);
  }

  /** Find category by source, external_id, and type */
  public Uni<Category> findBySourceCategoryType(
      Long sourceId, Integer categoryId, String categoryType) {
    String sql =
        "SELECT * FROM categories WHERE source_id = ? AND external_id = ? AND type = ?"
            + " LIMIT 1";
    return pool.preparedQuery(sql)
        .execute(Tuple.of(sourceId, categoryId, categoryType))
        .map(
            rowSet -> {
              if (rowSet.size() > 0) {
                return mapRow(rowSet.iterator().next());
              }
              return null;
            });
  }

  /**
   * Get or create "Unknown" category for a source and type Used when streams have no category
   * assigned
   *
   * @param sourceId Source ID
   * @param categoryType Category type (live, vod, series)
   * @return Database ID of the Unknown category
   */
  public Uni<Integer> getOrCreateUnknownCategory(Long sourceId, String categoryType) {
    return findBySourceCategoryType(sourceId, 0, categoryType)
        .flatMap(
            existing -> {
              if (existing != null) {
                return Uni.createFrom().item(existing.getExternalId());
              }

              // Create new Unknown category
              Category unknownCategory = new Category();
              unknownCategory.setSourceId(sourceId);
              unknownCategory.setExternalId(0);
              unknownCategory.setType(categoryType);
              unknownCategory.setName("Unknown");
              unknownCategory.setParentId(null);
              unknownCategory.setLabels("unknown");

              return insert(unknownCategory).replaceWith(0);
            });
  }

  /** Find entities by source ID */
  public Multi<Integer> findExternalIdsBySourceIdAndType(Long sourceId, StreamType type) {
    return pool.preparedQuery(
            "SELECT external_id FROM " + getTableName() + " WHERE source_id = ? AND type = ?")
        .execute(Tuple.of(sourceId, type.getCategoryType()))
        .onItem()
        .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
        .map(row -> row.getInteger("external_id"));
  }

  public Uni<Category> findByExternalIdAndType(Integer externalId, Long sourceId, StreamType type) {
    return pool.preparedQuery(
            "SELECT * FROM "
                + getTableName()
                + " WHERE external_id = ? AND source_id = ? AND type = ?")
        .execute(Tuple.of(externalId, sourceId, type.getCategoryType()))
        .map(rowSet -> rowSet.size() == 0 ? null : mapRow(rowSet.iterator().next()));
  }

  public Uni<Void> deleteByExternalIdAndType(Integer externalId, Long sourceId, StreamType type) {
    return pool.preparedQuery(
            "DELETE FROM "
                + getTableName()
                + " WHERE external_id = ? AND source_id = ? AND type = ?")
        .execute(Tuple.of(externalId, sourceId, type.getCategoryType()))
        .replaceWithVoid();
  }
}
