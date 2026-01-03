package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Category;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
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
}
