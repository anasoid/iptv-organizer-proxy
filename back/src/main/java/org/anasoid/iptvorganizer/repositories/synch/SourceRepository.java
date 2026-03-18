package org.anasoid.iptvorganizer.repositories.synch;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
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
  protected Long internalInsert(Source source) {
    String sql =
        "INSERT INTO sources (name, url, username, password, sync_interval, is_active, proxy_id, enable_proxy, connect_xtream_api, connect_xtream_stream, connect_xmltv, black_list_filter) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setString(1, source.getName());
      stmt.setString(2, source.getUrl());
      stmt.setString(3, source.getUsername());
      stmt.setString(4, source.getPassword());
      stmt.setInt(5, source.getSyncInterval());
      stmt.setBoolean(6, source.getIsActive());
      stmt.setObject(7, source.getProxyId());
      stmt.setObject(8, source.getEnableProxy());
      stmt.setString(
          9,
          source.getConnectXtreamApi() != null ? source.getConnectXtreamApi().name() : "DEFAULT");
      stmt.setString(
          10,
          source.getConnectXtreamStream() != null
              ? source.getConnectXtreamStream().name()
              : "DEFAULT");
      stmt.setString(
          11, source.getConnectXmltv() != null ? source.getConnectXmltv().name() : "DEFAULT");
      stmt.setString(12, source.getBlackListFilter());
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
  protected void internalUpdate(Source source) {
    String sql =
        "UPDATE sources SET name = ?, url = ?, username = ?, password = ?, sync_interval = ?, last_sync = ?, next_sync = ?, is_active = ?, proxy_id = ?, enable_proxy = ?, connect_xtream_api = ?, connect_xtream_stream = ?, connect_xmltv = ?, black_list_filter = ? WHERE id = ?";
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
      stmt.setObject(10, source.getEnableProxy());
      stmt.setString(
          11,
          source.getConnectXtreamApi() != null ? source.getConnectXtreamApi().name() : "DEFAULT");
      stmt.setString(
          12,
          source.getConnectXtreamStream() != null
              ? source.getConnectXtreamStream().name()
              : "DEFAULT");
      stmt.setString(
          13, source.getConnectXmltv() != null ? source.getConnectXmltv().name() : "DEFAULT");
      stmt.setString(14, source.getBlackListFilter());
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

    // Parse enum fields
    ConnectXtreamApiMode connectXtreamApi = null;
    String apiModeStr = rs.getString("connect_xtream_api");
    if (apiModeStr != null) {
      try {
        connectXtreamApi = ConnectXtreamApiMode.valueOf(apiModeStr);
      } catch (IllegalArgumentException e) {
        connectXtreamApi = ConnectXtreamApiMode.DEFAULT;
      }
    }

    ConnectXtreamStreamMode connectXtreamStream = null;
    String streamModeStr = rs.getString("connect_xtream_stream");
    if (streamModeStr != null) {
      try {
        connectXtreamStream = ConnectXtreamStreamMode.valueOf(streamModeStr);
      } catch (IllegalArgumentException e) {
        connectXtreamStream = ConnectXtreamStreamMode.DEFAULT;
      }
    }

    ConnectXmltvMode connectXmltv = null;
    String xmltvModeStr = rs.getString("connect_xmltv");
    if (xmltvModeStr != null) {
      try {
        connectXmltv = ConnectXmltvMode.valueOf(xmltvModeStr);
      } catch (IllegalArgumentException e) {
        connectXmltv = ConnectXmltvMode.DEFAULT;
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
        .enableProxy(toBoolean(rs, "enable_proxy"))
        .connectXtreamApi(connectXtreamApi)
        .connectXtreamStream(connectXtreamStream)
        .connectXmltv(connectXmltv)
        .blackListFilter(rs.getString("black_list_filter"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
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
