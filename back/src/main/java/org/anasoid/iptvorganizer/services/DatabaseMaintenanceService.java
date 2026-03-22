package org.anasoid.iptvorganizer.services;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class DatabaseMaintenanceService {

  @ConfigProperty(name = "app.datasource.dialect", defaultValue = "sqlite")
  String dialect;

  @ConfigProperty(name = "quarkus.datasource.jdbc.url", defaultValue = "")
  String jdbcUrl;

  @Inject DataSource dataSource;

  public boolean isSQLiteDialect() {
    return "sqlite".equalsIgnoreCase(dialect);
  }

  public DatabaseShrinkResult shrinkDatabase() throws Exception {
    Path dbPath = resolveSqlitePath();
    long sizeBeforeBytes = Files.exists(dbPath) ? Files.size(dbPath) : 0L;

    long startNanos = System.nanoTime();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("VACUUM");
    }
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;

    long sizeAfterBytes = Files.exists(dbPath) ? Files.size(dbPath) : sizeBeforeBytes;
    long freedBytes = Math.max(0L, sizeBeforeBytes - sizeAfterBytes);

    log.info(
        "Database shrink finished: before={} bytes, after={} bytes, freed={} bytes, duration={} ms",
        sizeBeforeBytes,
        sizeAfterBytes,
        freedBytes,
        durationMs);

    return new DatabaseShrinkResult(
        dialect.toLowerCase(),
        dbPath.toString(),
        sizeBeforeBytes,
        sizeAfterBytes,
        freedBytes,
        durationMs);
  }

  private Path resolveSqlitePath() {
    String prefix = "jdbc:sqlite:";
    if (!jdbcUrl.startsWith(prefix)) {
      throw new IllegalStateException("Unsupported SQLite JDBC URL: " + jdbcUrl);
    }

    String rawPath = jdbcUrl.substring(prefix.length());
    int queryIndex = rawPath.indexOf('?');
    if (queryIndex >= 0) {
      rawPath = rawPath.substring(0, queryIndex);
    }

    if (rawPath.startsWith("file:")) {
      rawPath = rawPath.substring("file:".length());
    }

    if (rawPath.isBlank()) {
      throw new IllegalStateException("Could not resolve SQLite database file path");
    }

    return Path.of(rawPath).toAbsolutePath().normalize();
  }

  @RegisterForReflection
  public record DatabaseShrinkResult(
      String dialect,
      String databasePath,
      long sizeBeforeBytes,
      long sizeAfterBytes,
      long freedBytes,
      long durationMs) {}
}
