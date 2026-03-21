package org.anasoid.iptvorganizer;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.migrations.SimpleMigrator;
import org.anasoid.iptvorganizer.services.synch.SyncLogService;
import org.anasoid.iptvorganizer.services.synch.SyncManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
@Startup
public class IpTvOrganizerApplication {

  @Inject SimpleMigrator simpleMigrator;
  @Inject SyncManager syncManager;
  @Inject SyncLogService syncLogService;
  @Inject DataSource dataSource;

  @ConfigProperty(name = "app.datasource.dialect", defaultValue = "sqlite")
  String datasourceDialect;

  @ConfigProperty(name = "quarkus.datasource.jdbc.max-size", defaultValue = "5")
  int jdbcMaxPoolSize;

  @ConfigProperty(name = "quarkus.datasource.jdbc.additional-jdbc-properties.cache_size")
  String sqliteCacheSize;

  @ConfigProperty(name = "quarkus.datasource.jdbc.additional-jdbc-properties.temp_store")
  String sqliteTempStore;

  @ConfigProperty(name = "quarkus.datasource.jdbc.additional-jdbc-properties.journal_mode")
  String sqliteJournalMode;

  @ConfigProperty(name = "quarkus.datasource.jdbc.additional-jdbc-properties.synchronous")
  String sqliteSynchronous;

  /**
   * Scheduled task to check for sources needing sync Runs every 5 minutes by default, configurable
   * via sync.check.interval
   */
  @Scheduled(every = "{sync.check.interval}", identity = "sync-daemon")
  public void scheduledSync() {
    syncManager.scheduledSync();
  }

  void onStart(@Observes StartupEvent event) {
    log.debug("Startup event received: {}", event);
    log.info("On startup IpTvOrganizerApplication");

    simpleMigrator.startMigrations();
    logSqliteInfoConfig();
    syncLogService.fixInterruptedSyncs();
    log.info("On startup IpTvOrganizerApplication done");
  }

  private void logSqliteInfoConfig() {
    if (!"sqlite".equalsIgnoreCase(datasourceDialect)) {
      return;
    }
    log.info("=============================SQLITE==========================");
    logSqliteMemoryTuningConfig();
    logSqliteRuntimeInfo();
    log.info("==============================================================");
  }

  private void logSqliteMemoryTuningConfig() {

    log.info(
        "-- SQLite memory tuning: pool.max-size={}, cache_size={}, temp_store={}, journal_mode={}, synchronous={}",
        jdbcMaxPoolSize,
        sqliteCacheSize,
        sqliteTempStore,
        sqliteJournalMode,
        sqliteSynchronous);
  }

  private void logSqliteRuntimeInfo() {

    try (Connection conn = dataSource.getConnection()) {
      String sqliteVersion;
      try (Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("SELECT sqlite_version()")) {
        if (!rs.next()) {
          throw new IllegalStateException("No row returned for query: SELECT sqlite_version()");
        }
        sqliteVersion = rs.getString(1);
      }

      long pageSize = queryLong(conn, "PRAGMA page_size");
      long pageCount = queryLong(conn, "PRAGMA page_count");
      long freelistCount = queryLong(conn, "PRAGMA freelist_count");
      long runtimeCacheSize = queryLong(conn, "PRAGMA cache_size");
      String runtimeJournalMode;
      try (Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
        if (!rs.next()) {
          throw new IllegalStateException("No row returned for query: PRAGMA journal_mode");
        }
        runtimeJournalMode = rs.getString(1);
      }
      String runtimeTempStore = normalizeTempStore(queryLong(conn, "PRAGMA temp_store"));
      String runtimeSynchronous = normalizeSynchronous(queryLong(conn, "PRAGMA synchronous"));
      long tableCount =
          queryLong(
              conn,
              "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");

      long fileBytes = pageSize * pageCount;
      long usedBytes = pageSize * Math.max(0L, pageCount - freelistCount);

      log.info(
          "-- SQLite runtime info: version={}, tables={}, page_size={}, page_count={}, freelist_count={}, file_bytes={}, used_bytes={}",
          sqliteVersion,
          tableCount,
          pageSize,
          pageCount,
          freelistCount,
          fileBytes,
          usedBytes);

      log.info(
          "-- SQLite pragma check: cache_size={} (runtime={}), temp_store={} (runtime={}), journal_mode={} (runtime={}), synchronous={} (runtime={})",
          sqliteCacheSize,
          runtimeCacheSize,
          sqliteTempStore,
          runtimeTempStore,
          sqliteJournalMode,
          runtimeJournalMode,
          sqliteSynchronous,
          runtimeSynchronous);
    } catch (Exception e) {
      log.warn("Unable to read SQLite runtime info at startup", e);
    }
  }

  private long queryLong(Connection conn, String sql) throws Exception {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      if (rs.next()) {
        return rs.getLong(1);
      }
      throw new IllegalStateException("No row returned for query: " + sql);
    }
  }

  private String normalizeTempStore(long tempStoreValue) {
    return switch ((int) tempStoreValue) {
      case 0 -> "DEFAULT";
      case 1 -> "FILE";
      case 2 -> "MEMORY";
      default -> String.valueOf(tempStoreValue);
    };
  }

  private String normalizeSynchronous(long synchronousValue) {
    return switch ((int) synchronousValue) {
      case 0 -> "OFF";
      case 1 -> "NORMAL";
      case 2 -> "FULL";
      case 3 -> "EXTRA";
      default -> String.valueOf(synchronousValue);
    };
  }
}
