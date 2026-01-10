package org.anasoid.iptvorganizer.repositories;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.models.AdminUser;

@ApplicationScoped
public class AdminUserRepository extends BaseRepository<AdminUser> {

  @Override
  protected String getTableName() {
    return "admin_users";
  }

  @Override
  public Uni<Long> insert(AdminUser user) {
    String sql =
        "INSERT INTO admin_users (username, password_hash, email, is_active) VALUES (?, ?, ?, ?)";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                user.getUsername(), user.getPasswordHash(), user.getEmail(), user.getIsActive()))
        .map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(AdminUser user) {
    String sql =
        "UPDATE admin_users SET username = ?, password_hash = ?, email = ?, is_active = ? WHERE id"
            + " = ?";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getIsActive(),
                user.getId()))
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

  /** Find admin user by username */
  public Uni<AdminUser> findByUsername(String username) {
    String sql = "SELECT * FROM admin_users WHERE username = ?";
    return pool.preparedQuery(sql)
        .execute(Tuple.of(username))
        .map(rowSet -> rowSet.size() == 0 ? null : mapRow(rowSet.iterator().next()));
  }

  /** Update last login timestamp */
  public Uni<Void> updateLastLogin(Long userId, LocalDateTime lastLogin) {
    String sql = "UPDATE admin_users SET last_login = ? WHERE id = ?";
    return pool.preparedQuery(sql).execute(Tuple.of(lastLogin, userId)).replaceWithVoid();
  }

  /** Check if username exists */
  public Uni<Boolean> usernameExists(String username) {
    String sql = "SELECT COUNT(*) as count FROM admin_users WHERE username = ?";
    return pool.preparedQuery(sql)
        .execute(Tuple.of(username))
        .map(rowSet -> rowSet.iterator().next().getInteger("count") > 0);
  }
}
