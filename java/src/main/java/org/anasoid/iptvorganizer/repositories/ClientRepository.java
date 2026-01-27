package org.anasoid.iptvorganizer.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Client;

@ApplicationScoped
public class ClientRepository extends BaseRepository<Client> {

  @Override
  protected String getTableName() {
    return "clients";
  }

  @Override
  public Long insert(Client client) {
    String sql =
        "INSERT INTO clients (source_id, filter_id, username, password, name, email, expiry_date, is_active, hide_adult_content, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, client.getSourceId());
      stmt.setLong(2, client.getFilterId());
      stmt.setString(3, client.getUsername());
      stmt.setString(4, client.getPassword());
      stmt.setString(5, client.getName());
      stmt.setString(6, client.getEmail());
      stmt.setObject(7, client.getExpiryDate());
      stmt.setBoolean(8, client.getIsActive());
      stmt.setBoolean(9, client.getHideAdultContent());
      stmt.setString(10, client.getNotes());
      stmt.executeUpdate();
      return getGeneratedId(stmt);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert client", e);
    }
  }

  @Override
  public void update(Client client) {
    String sql =
        "UPDATE clients SET source_id = ?, filter_id = ?, username = ?, password = ?, name = ?, email = ?, expiry_date = ?, is_active = ?, hide_adult_content = ?, notes = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, client.getSourceId());
      stmt.setLong(2, client.getFilterId());
      stmt.setString(3, client.getUsername());
      stmt.setString(4, client.getPassword());
      stmt.setString(5, client.getName());
      stmt.setString(6, client.getEmail());
      stmt.setObject(7, client.getExpiryDate());
      stmt.setBoolean(8, client.getIsActive());
      stmt.setBoolean(9, client.getHideAdultContent());
      stmt.setString(10, client.getNotes());
      stmt.setLong(11, client.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update client", e);
    }
  }

  @Override
  protected Client mapRow(ResultSet rs) throws SQLException {
    return Client.builder()
        .id(rs.getLong("id"))
        .sourceId(rs.getLong("source_id"))
        .filterId(rs.getLong("filter_id"))
        .username(rs.getString("username"))
        .password(rs.getString("password"))
        .name(rs.getString("name"))
        .email(rs.getString("email"))
        .expiryDate(rs.getObject("expiry_date", LocalDate.class))
        .isActive(rs.getBoolean("is_active"))
        .hideAdultContent(rs.getBoolean("hide_adult_content"))
        .notes(rs.getString("notes"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .lastLogin(rs.getObject("last_login", LocalDateTime.class))
        .build();
  }

  /** Search clients by username, name, or email with pagination */
  public List<Client> searchClients(String search, int page, int limit) {
    String whereClause = "(username LIKE ? OR name LIKE ? OR email LIKE ?)";
    String searchTerm = "%" + search + "%";
    return findWherePaged(whereClause, page, limit, "id DESC", searchTerm, searchTerm, searchTerm);
  }

  /** Count clients matching search criteria */
  public Long countSearchClients(String search) {
    String whereClause = "(username LIKE ? OR name LIKE ? OR email LIKE ?)";
    String searchTerm = "%" + search + "%";
    return countWhere(whereClause, searchTerm, searchTerm, searchTerm);
  }

  /** Find client by username */
  public Client findByUsername(String username) {
    String sql = "SELECT * FROM clients WHERE username = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, username);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next() ? mapRow(rs) : null;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find client by username: " + username, e);
    }
  }

  /** Find client by username */
  public Client findByUsernameAndPassword(String username, String password) {
    // Validate inputs
    if (username == null || username.trim().isEmpty()) {
      throw new RuntimeException("Username is required");
    }
    Client client = this.findByUsername(username);
    if (client == null) {
      throw new RuntimeException("Client not found");
    }

    // Validate password
    if (password == null || !password.equals(client.getPassword())) {
      throw new RuntimeException("Invalid password");
    }

    // Check if client is active
    if (client.getIsActive() != null && !client.getIsActive()) {
      throw new RuntimeException("Client is inactive");
    }
    return client;
  }
}
