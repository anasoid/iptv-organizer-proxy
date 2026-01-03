package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Category;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

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
}
