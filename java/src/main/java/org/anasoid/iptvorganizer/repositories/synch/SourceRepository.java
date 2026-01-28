package org.anasoid.iptvorganizer.repositories.synch;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

@ApplicationScoped
public class SourceRepository extends BaseRepository<Source> {

  @Override
  protected String getTableName() {
    return "sources";
  }

  /** Find all sources that need syncing (next_sync_time <= now and is_active = true) */
  public List<Source> findSourcesNeedingSync() {
    List<Source> results = new ArrayList<>();
    String sql =
        "SELECT * FROM sources WHERE next_sync <= ? AND is_active = true ORDER BY next_sync ASC";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, LocalDateTime.now());
      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          results.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to find sources needing sync", e);
    }
    return results;
  }

  @Override
  public Long insert(Source source) {
    String sql =
        "INSERT INTO sources (name, url, username, password, sync_interval, is_active, proxy_id, enableproxy, disablestreamproxy, stream_follow_location, use_redirect, use_redirect_xmltv) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setString(1, source.getName());
      stmt.setString(2, source.getUrl());
      stmt.setString(3, source.getUsername());
      stmt.setString(4, source.getPassword());
      stmt.setInt(5, source.getSyncInterval());
      stmt.setBoolean(6, source.getIsActive());
      stmt.setObject(7, source.getProxyId());
      stmt.setBoolean(8, source.getEnableProxy());
      stmt.setBoolean(9, source.getDisableStreamProxy());
      stmt.setBoolean(10, source.getStreamFollowLocation());
      stmt.setObject(11, source.getUseRedirect());
      stmt.setObject(12, source.getUseRedirectXmltv());
      stmt.executeUpdate();

      // Get generated key using standard JDBC approach - works with MySQL, H2, SQLite
      try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
        if (generatedKeys.next()) {
          Long id = generatedKeys.getLong(1);
          source.setId(id);
          return id;
        }
      }
      throw new IllegalStateException("Unable to retrieve inserted ID for source");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert source", e);
    }
  }

  @Override
  public void update(Source source) {
    String sql =
        "UPDATE sources SET name = ?, url = ?, username = ?, password = ?, sync_interval = ?, last_sync = ?, next_sync = ?, is_active = ?, proxy_id = ?, enableproxy = ?, disablestreamproxy = ?, stream_follow_location = ?, use_redirect = ?, use_redirect_xmltv = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, source.getName());
      stmt.setString(2, source.getUrl());
      stmt.setString(3, source.getUsername());
      stmt.setString(4, source.getPassword());
      stmt.setInt(5, source.getSyncInterval());
      stmt.setObject(6, source.getLastSync());
      stmt.setObject(7, source.getNextSync());
      stmt.setBoolean(8, source.getIsActive());
      stmt.setObject(9, source.getProxyId());
      stmt.setBoolean(10, source.getEnableProxy());
      stmt.setBoolean(11, source.getDisableStreamProxy());
      stmt.setBoolean(12, source.getStreamFollowLocation());
      stmt.setObject(13, source.getUseRedirect());
      stmt.setObject(14, source.getUseRedirectXmltv());
      stmt.setLong(15, source.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update source", e);
    }
  }

  @Override
  protected Source mapRow(ResultSet rs) throws SQLException {
    // Handle proxy_id conversion from Integer/Long to Long
    Object proxyIdObj = rs.getObject("proxy_id");
    Long proxyId = null;
    if (proxyIdObj != null) {
      if (proxyIdObj instanceof Number) {
        proxyId = ((Number) proxyIdObj).longValue();
      } else {
        proxyId = Long.valueOf(proxyIdObj.toString());
      }
    }

    return Source.builder()
        .id(rs.getLong("id"))
        .name(rs.getString("name"))
        .url(rs.getString("url"))
        .username(rs.getString("username"))
        .password(rs.getString("password"))
        .syncInterval(rs.getInt("sync_interval"))
        .lastSync(rs.getObject("last_sync", LocalDateTime.class))
        .nextSync(rs.getObject("next_sync", LocalDateTime.class))
        .isActive(rs.getBoolean("is_active"))
        .proxyId(proxyId)
        .enableProxy(rs.getBoolean("enableproxy"))
        .disableStreamProxy(rs.getBoolean("disablestreamproxy"))
        .streamFollowLocation(rs.getBoolean("stream_follow_location"))
        .useRedirect(rs.getObject("use_redirect", Boolean.class))
        .useRedirectXmltv(rs.getObject("use_redirect_xmltv", Boolean.class))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
  }
}
