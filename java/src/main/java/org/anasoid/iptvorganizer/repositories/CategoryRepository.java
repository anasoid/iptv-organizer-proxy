package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Category;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class CategoryRepository extends BaseRepository<Category> {

    @Override
    protected String getTableName() {
        return "categories";
    }

    @Override
    public Uni<Long> insert(Category category) {
        String sql = "INSERT INTO categories (source_id, category_id, category_name, category_type, num, allow_deny, parent_id, labels) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(category.getSourceId())
            .addInteger(category.getCategoryId())
            .addString(category.getCategoryName())
            .addString(category.getCategoryType())
            .addInteger(category.getNum())
            .addString(category.getAllowDeny())
            .addInteger(category.getParentId())
            .addString(category.getLabels());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(Category category) {
        String sql = "UPDATE categories SET source_id = ?, category_id = ?, category_name = ?, category_type = ?, num = ?, allow_deny = ?, parent_id = ?, labels = ? WHERE id = ?";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(category.getSourceId())
            .addInteger(category.getCategoryId())
            .addString(category.getCategoryName())
            .addString(category.getCategoryType())
            .addInteger(category.getNum())
            .addString(category.getAllowDeny())
            .addInteger(category.getParentId())
            .addString(category.getLabels())
            .addLong(category.getId());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
    }

    @Override
    protected Category mapRow(Row row) {
        return Category.builder()
            .id(row.getLong("id"))
            .sourceId(row.getLong("source_id"))
            .categoryId(row.getInteger("category_id"))
            .categoryName(row.getString("category_name"))
            .categoryType(row.getString("category_type"))
            .num(row.getInteger("num"))
            .allowDeny(row.getString("allow_deny"))
            .parentId(row.getInteger("parent_id"))
            .labels(row.getString("labels"))
            .createdAt(row.getLocalDateTime("created_at"))
            .build();
    }

    /**
     * Find categories by source ID with optional category type filter and search
     */
    public Multi<Category> findBySourceIdFiltered(Long sourceId, String categoryType, String search, int page, int limit) {
        StringBuilder whereClause = new StringBuilder("source_id = ?");
        Tuple params = Tuple.of(sourceId);

        if (categoryType != null && !categoryType.isEmpty()) {
            whereClause.append(" AND category_type = ?");
            params = params.addString(categoryType);
        }

        if (search != null && !search.isEmpty()) {
            whereClause.append(" AND category_name LIKE ?");
            params = params.addString("%" + search + "%");
        }

        return findWherePaged(whereClause.toString(), params, page, limit, "id DESC");
    }

    /**
     * Count categories by source ID with optional filters
     */
    public Uni<Long> countBySourceIdFiltered(Long sourceId, String categoryType, String search) {
        StringBuilder whereClause = new StringBuilder("source_id = ?");
        Tuple params = Tuple.of(sourceId);

        if (categoryType != null && !categoryType.isEmpty()) {
            whereClause.append(" AND category_type = ?");
            params = params.addString(categoryType);
        }

        if (search != null && !search.isEmpty()) {
            whereClause.append(" AND category_name LIKE ?");
            params = params.addString("%" + search + "%");
        }

        return countWhere(whereClause.toString(), params);
    }

    /**
     * Find categories by source ID
     */
    public Multi<Category> findBySourceId(Long sourceId) {
        return pool.preparedQuery("SELECT * FROM categories WHERE source_id = ? ORDER BY id DESC")
            .execute(Tuple.of(sourceId))
            .onItem()
            .transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .map(this::mapRow);
    }

    /**
     * Batch upsert categories using INSERT ... ON DUPLICATE KEY UPDATE
     * Efficiently inserts or updates multiple categories in a single operation
     */
    public Uni<Void> batchUpsert(List<Category> categories) {
        String baseSql = "INSERT INTO categories (source_id, category_id, category_name, " +
                         "category_type, num, allow_deny, parent_id, labels) VALUES ";

        String updateClause = " ON DUPLICATE KEY UPDATE " +
                             "category_name = VALUES(category_name), " +
                             "num = VALUES(num), " +
                             "allow_deny = VALUES(allow_deny), " +
                             "parent_id = VALUES(parent_id), " +
                             "labels = VALUES(labels)";

        return executeBatchUpsert(categories, category -> Tuple.tuple()
            .addLong(category.getSourceId())
            .addInteger(category.getCategoryId())
            .addString(category.getCategoryName())
            .addString(category.getCategoryType())
            .addInteger(category.getNum())
            .addString(category.getAllowDeny())
            .addInteger(category.getParentId())
            .addString(category.getLabels()),
            baseSql, updateClause
        );
    }

    /**
     * Get all category IDs for a given source and type
     * Used to determine which categories exist and which need to be deleted
     */
    public Uni<Set<Integer>> getCategoryIdsBySourceAndType(Long sourceId, String categoryType) {
        String sql = "SELECT category_id FROM categories WHERE source_id = ? AND category_type = ?";
        return pool.preparedQuery(sql)
            .execute(Tuple.of(sourceId, categoryType))
            .map(rowSet -> {
                Set<Integer> categoryIds = new HashSet<>();
                rowSet.forEach(row -> categoryIds.add(row.getInteger("category_id")));
                return categoryIds;
            });
    }

    /**
     * Delete all categories for a source and type that are NOT in the keepIds set
     * Used to remove obsolete categories after sync
     */
    public Uni<Integer> deleteCategoriesNotInSet(Long sourceId, String categoryType, Set<Integer> keepIds) {
        if (keepIds.isEmpty()) {
            // Delete all categories for this source and type
            String sql = "DELETE FROM categories WHERE source_id = ? AND category_type = ?";
            return pool.preparedQuery(sql)
                .execute(Tuple.of(sourceId, categoryType))
                .map(rowSet -> rowSet.rowCount());
        }

        // Delete categories not in keepIds
        StringBuilder sql = new StringBuilder("DELETE FROM categories WHERE source_id = ? AND category_type = ? AND category_id NOT IN (");
        Tuple params = Tuple.of(sourceId, categoryType);

        int i = 0;
        for (Integer categoryId : keepIds) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            params.addInteger(categoryId);
            i++;
        }
        sql.append(")");

        return pool.preparedQuery(sql.toString())
            .execute(params)
            .map(rowSet -> rowSet.rowCount());
    }
}
