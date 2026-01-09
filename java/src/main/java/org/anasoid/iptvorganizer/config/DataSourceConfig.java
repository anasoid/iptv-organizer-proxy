package org.anasoid.iptvorganizer.config;

import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DataSourceConfig {

  private static final Logger LOGGER = Logger.getLogger(DataSourceConfig.class.getName());

  @Inject Vertx vertx;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.db-kind")
  String dbKind;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.reactive.url", defaultValue = "")
  String url;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.username")
  String username;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.password")
  String password;

  @Inject
  @ConfigProperty(name = "quarkus.datasource.reactive.max-size", defaultValue = "2")
  int maxSize;

  @Produces
  @ApplicationScoped
  public Pool pool() {
    if ("h2".equalsIgnoreCase(dbKind)) {
      // H2 is JDBC-only, doesn't have reactive support
      // In test environment with H2TestProfile, tests use JDBC directly
      // For production, use MySQL or PostgreSQL
      LOGGER.info("H2 database detected - no reactive pool available. Using JDBC connection pool.");
      return null;
    }

    if ("mysql".equalsIgnoreCase(dbKind)) {
      LOGGER.info("Creating reactive MySQL pool");
      return createMySQLPool();
    }

    if ("postgresql".equalsIgnoreCase(dbKind) || "postgres".equalsIgnoreCase(dbKind)) {
      LOGGER.info("Creating reactive PostgreSQL pool");
      return createPostgresPool();
    }

    throw new IllegalArgumentException("Unsupported database kind: " + dbKind);
  }

  private Pool createMySQLPool() {
    MySQLConnectOptions options =
        MySQLConnectOptions.fromUri(url).setUser(username).setPassword(password);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(maxSize);

    return MySQLPool.pool(vertx, options, poolOptions);
  }

  private Pool createPostgresPool() {
    PgConnectOptions options =
        PgConnectOptions.fromUri(url).setUser(username).setPassword(password);

    PoolOptions poolOptions = new PoolOptions().setMaxSize(maxSize);

    return PgPool.pool(vertx, options, poolOptions);
  }
}
