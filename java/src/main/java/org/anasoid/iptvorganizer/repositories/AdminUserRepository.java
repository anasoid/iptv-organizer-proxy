package org.anasoid.iptvorganizer.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.models.entity.AdminUser;

@ApplicationScoped
public class AdminUserRepository extends BaseRepository<AdminUser> {

  @Override
  protected String getTableName() {
    return "admin_users";
  }

  @Override
  public Long insert(AdminUser user) {
    String sql =
        "INSERT INTO admin_users (username, password_hash, email, is_active) VALUES (?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setString(1, user.getUsername());
      stmt.setString(2, user.getPasswordHash());
      stmt.setString(3, user.getEmail());
      stmt.setBoolean(4, user.getIsActive());
      stmt.executeUpdate();
      return getGeneratedId(stmt);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert admin user", e);
    }
  }

  @Override
  public void update(AdminUser user) {
    String sql =
        "UPDATE admin_users SET username = ?, password_hash = ?, email = ?, is_active = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, user.getUsername());
      stmt.setString(2, user.getPasswordHash());
      stmt.setString(3, user.getEmail());
      stmt.setBoolean(4, user.getIsActive());
      stmt.setLong(5, user.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update admin user", e);
    }
  }

  @Override
  protected AdminUser mapRow(ResultSet rs) throws SQLException {
    return AdminUser.builder()
        .id(rs.getLong("id"))
        .username(rs.getString("username"))
        .passwordHash(rs.getString("password_hash"))
        .email(rs.getString("email"))
        .isActive(rs.getBoolean("is_active"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .lastLogin(rs.getObject("last_login", LocalDateTime.class))
        .build();
  }

  /** Find admin user by username */
  public AdminUser findByUsername(String username) {
    String sql = "SELECT * FROM admin_users WHERE username = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, username);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? mapRow(rs) : null;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find admin user by username", e);
    }
  }

  /** Update last login timestamp */
  public void updateLastLogin(Long userId, LocalDateTime lastLogin) {
    String sql = "UPDATE admin_users SET last_login = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, lastLogin);
      stmt.setLong(2, userId);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update last login", e);
    }
  }

  /** Check if username exists */
  public Boolean usernameExists(String username) {
    String sql = "SELECT COUNT(*) as count FROM admin_users WHERE username = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, username);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() && rs.getInt("count") > 0;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check if username exists", e);
    }
  }
}
