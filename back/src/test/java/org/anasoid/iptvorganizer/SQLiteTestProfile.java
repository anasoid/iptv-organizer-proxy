package org.anasoid.iptvorganizer;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class SQLiteTestProfile implements QuarkusTestProfile {
  @Override
  public Map<String, String> getConfigOverrides() {
    // Use SQLite in-memory database for testing
    // jdbc:sqlite:file::memory:?cache=shared allows multiple connections in the pool
    // to share the same in-memory database instance
    return Map.of(
        "quarkus.datasource.db-kind", "other",
        "quarkus.datasource.jdbc.driver", "org.sqlite.JDBC",
        "quarkus.datasource.jdbc.url", "jdbc:sqlite:file::memory:?cache=shared",
        "quarkus.datasource.username", "",
        "quarkus.datasource.password", "",
        "quarkus.datasource.jdbc.min-size", "1",
        "quarkus.datasource.jdbc.max-size", "1",
        "quarkus.datasource.jdbc.enable-validation", "false",
        "quarkus.scheduler.enabled", "false",
        "app.datasource.dialect", "sqlite");
  }
}
