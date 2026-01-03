package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.AdminUser;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AdminUserRepository extends BaseRepository<AdminUser> {

    @Override
    protected String getTableName() {
        return "admin_users";
    }

    @Override
    public Uni<Long> insert(AdminUser user) {
        String sql = "INSERT INTO admin_users (username, password_hash, email, is_active) VALUES (?, ?, ?, ?)";
        return pool.preparedQuery(sql)
            .execute(Tuple.of(user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getIsActive()))
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(AdminUser user) {
        String sql = "UPDATE admin_users SET username = ?, password_hash = ?, email = ?, is_active = ? WHERE id = ?";
        return pool.preparedQuery(sql)
            .execute(Tuple.of(user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getIsActive(), user.getId()))
            .replaceWithVoid();
    }

    @Override
    protected AdminUser mapRow(Row row) {
        return AdminUser.builder()
            .id(row.getLong("id"))
            .username(row.getString("username"))
            .passwordHash(row.getString("password_hash"))
            .email(row.getString("email"))
            .isActive(row.getBoolean("is_active"))
            .createdAt(row.getLocalDateTime("created_at"))
            .updatedAt(row.getLocalDateTime("updated_at"))
            .lastLogin(row.getLocalDateTime("last_login"))
            .build();
    }
}
