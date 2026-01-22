package org.anasoid.iptvorganizer.migrations;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class SimpleMigrator {

  @Inject DataSource dataSource;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.db-kind")
  String dbKind;

  // Hardcoded migration list for native image compatibility
  // Updated as new migrations are added
  private static final List<String> MIGRATIONS =
      List.of(
          "V001__create_schema_version.sql",
          "V002__create_admin_users.sql",
          "V003__create_sources.sql",
          "V004__create_filters.sql",
          "V005__create_clients.sql",
          "V006__create_categories.sql",
          "V007__create_live_streams.sql",
          "V008__create_vod_streams.sql",
          "V009__create_series.sql",
          "V010__create_sync_logs.sql",
          "V011__create_connection_logs.sql",
          "V012__create_sync_schedule.sql");

  void onStart(@Observes StartupEvent event) {
    log.info("Starting database migrations for: " + dbKind);
    try {
      runMigrations();
    } catch (Exception e) {
      log.error("Migration failed", e);
      throw new RuntimeException("Database migration failed", e);
    }
  }

  private void runMigrations() throws Exception {
    ensureSchemaVersionTable();
    List<String> appliedVersions = getAppliedMigrations();

    List<String> pendingMigrations = new ArrayList<>();
    for (String migration : MIGRATIONS) {
      String version = getVersion(migration);
      if (!appliedVersions.contains(version)) {
        pendingMigrations.add(migration);
      }
    }

    if (pendingMigrations.isEmpty()) {
      log.info("No pending migrations");
      return;
    }

    for (String migration : pendingMigrations) {
      applyMigration(migration);
    }
  }

  private void ensureSchemaVersionTable() throws Exception {
    String createTableSql;
    if ("sqlite".equalsIgnoreCase(dbKind)) {
      createTableSql =
          """
          CREATE TABLE IF NOT EXISTS schema_version (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              version TEXT NOT NULL UNIQUE,
              description TEXT,
              checksum TEXT NOT NULL,
              applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )
          """;
    } else if ("mssql".equalsIgnoreCase(dbKind)) {
      // SQL Server
      createTableSql =
          """
          IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'schema_version')
          BEGIN
              CREATE TABLE schema_version (
                  id BIGINT IDENTITY(1,1) PRIMARY KEY,
                  version VARCHAR(255) NOT NULL UNIQUE,
                  description VARCHAR(500),
                  checksum VARCHAR(32) NOT NULL,
                  applied_at DATETIME2 DEFAULT GETDATE()
              )
              CREATE INDEX idx_version ON schema_version(version)
          END
          """;
    } else {
      // MySQL and PostgreSQL
      createTableSql =
          """
          CREATE TABLE IF NOT EXISTS schema_version (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              version VARCHAR(255) NOT NULL UNIQUE,
              description VARCHAR(500),
              checksum VARCHAR(32) NOT NULL,
              applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_version (version)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
          """;
    }

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(createTableSql);
      log.debug("schema_version table ensured");
    }
  }

  private List<String> getAppliedMigrations() {
    List<String> versions = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
      while (rs.next()) {
        versions.add(rs.getString("version"));
      }
      return versions;
    } catch (Exception e) {
      log.debug("schema_version table does not exist yet, treating as empty");
      return new ArrayList<>();
    }
  }

  private void applyMigration(String filename) throws Exception {
    String version = getVersion(filename);
    String description = getDescription(filename);
    String sql = loadSqlFile(filename);
    String checksum = calculateChecksum(sql);

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try {
        // Split SQL by semicolon and execute each statement
        String[] sqlStatements = sql.split(";");
        Statement stmt = conn.createStatement();

        for (String statement : sqlStatements) {
          String trimmed = statement.trim();
          if (!trimmed.isEmpty()) {
            stmt.execute(trimmed);
          }
        }
        stmt.close();

        // Record migration in schema_version
        String insertSql =
            "INSERT INTO schema_version (version, description, checksum) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
          pstmt.setString(1, version);
          pstmt.setString(2, description);
          pstmt.setString(3, checksum);
          pstmt.executeUpdate();
        }

        conn.commit();
        log.info("Applied migration: " + filename);
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    }
  }

  private String loadSqlFile(String filename) {
    String path = "/db/migration/" + dbKind + "/" + filename;
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalStateException("Migration file not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load migration file: " + filename, e);
    }
  }

  private String calculateChecksum(String content) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to calculate checksum", e);
    }
  }

  private String getVersion(String filename) {
    // Extract "V001" from "V001__description.sql"
    if (filename.contains("__")) {
      return filename.substring(0, filename.indexOf("__"));
    }
    throw new IllegalArgumentException("Invalid migration filename format: " + filename);
  }

  private String getDescription(String filename) {
    // Extract "create schema version" from "V001__create_schema_version.sql"
    if (filename.contains("__")) {
      String withoutVersion = filename.substring(filename.indexOf("__") + 2);
      String withoutExtension = withoutVersion.replace(".sql", "");
      return withoutExtension.replace("_", " ");
    }
    throw new IllegalArgumentException("Invalid migration filename format: " + filename);
  }
}
