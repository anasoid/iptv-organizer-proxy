package org.anasoid.iptvorganizer.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;

@ApplicationScoped
public class ProxyRepository extends BaseRepository<Proxy> {

  @Override
  protected String getTableName() {
    return "proxies";
  }

  @Override
  protected Long internalInsert(Proxy proxy) {
    String sql =
        "INSERT INTO proxies (name, description, proxy_url, proxy_host, proxy_port, proxy_type, "
            + "proxy_username, proxy_password, timeout, max_retries, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setString(1, proxy.getName());
      stmt.setString(2, proxy.getDescription());
      stmt.setString(3, proxy.getProxyUrl());
      stmt.setString(4, proxy.getProxyHost());
      stmt.setObject(5, proxy.getProxyPort());
      stmt.setString(6, proxy.getProxyType() != null ? proxy.getProxyType().name() : null);
      stmt.setString(7, proxy.getProxyUsername());
      stmt.setString(8, proxy.getProxyPassword());
      stmt.setObject(9, proxy.getTimeout());
      stmt.setObject(10, proxy.getMaxRetries());
      stmt.setObject(11, proxy.getCreatedAt());
      stmt.setObject(12, proxy.getUpdatedAt());

      stmt.executeUpdate();
      return getGeneratedId(stmt);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert proxy", e);
    }
  }

  @Override
  protected void internalUpdate(Proxy proxy) {
    String sql =
        "UPDATE proxies SET name = ?, description = ?, proxy_url = ?, proxy_host = ?, "
            + "proxy_port = ?, proxy_type = ?, proxy_username = ?, proxy_password = ?, "
            + "timeout = ?, max_retries = ?, updated_at = ? WHERE id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, proxy.getName());
      stmt.setString(2, proxy.getDescription());
      stmt.setString(3, proxy.getProxyUrl());
      stmt.setString(4, proxy.getProxyHost());
      stmt.setObject(5, proxy.getProxyPort());
      stmt.setString(6, proxy.getProxyType() != null ? proxy.getProxyType().name() : null);
      stmt.setString(7, proxy.getProxyUsername());
      stmt.setString(8, proxy.getProxyPassword());
      stmt.setObject(9, proxy.getTimeout());
      stmt.setObject(10, proxy.getMaxRetries());
      stmt.setObject(11, proxy.getUpdatedAt());
      stmt.setLong(12, proxy.getId());

      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update proxy", e);
    }
  }

  @Override
  protected Proxy mapRow(ResultSet rs) throws SQLException {
    return Proxy.builder()
        .id(rs.getLong("id"))
        .name(rs.getString("name"))
        .description(rs.getString("description"))
        .proxyUrl(rs.getString("proxy_url"))
        .proxyHost(rs.getString("proxy_host"))
        .proxyPort((Integer) rs.getObject("proxy_port"))
        .proxyType(
            rs.getString("proxy_type") != null
                ? ProxyType.valueOf(rs.getString("proxy_type"))
                : null)
        .proxyUsername(rs.getString("proxy_username"))
        .proxyPassword(rs.getString("proxy_password"))
        .timeout((Integer) rs.getObject("timeout"))
        .maxRetries((Integer) rs.getObject("max_retries"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
  }

  public Optional<Proxy> findByName(String name) {
    String sql = "SELECT * FROM proxies WHERE name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, name);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find proxy by name: " + name, e);
    }
    return Optional.empty();
  }

  public boolean nameExists(String name) {
    String sql = "SELECT COUNT(*) FROM proxies WHERE name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, name);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1) > 0;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to check if proxy name exists: " + name, e);
    }
    return false;
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
