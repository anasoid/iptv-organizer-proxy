package org.anasoid.iptvorganizer.config;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.util.logging.Logger;

@ApplicationScoped
public class DataSourceConfig {

    private static final Logger LOGGER = Logger.getLogger(DataSourceConfig.class.getName());

    @Inject
    Vertx vertx;

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
            LOGGER.warning("H2 database selected but reactive pool is not available. Using null pool - H2 not fully supported yet.");
            return null;
        } else if ("mysql".equalsIgnoreCase(dbKind)) {
            LOGGER.info("Creating reactive MySQL pool");
            return createMySQLPool();
        } else if ("postgresql".equalsIgnoreCase(dbKind) || "postgres".equalsIgnoreCase(dbKind)) {
            LOGGER.info("Creating reactive PostgreSQL pool");
            return createPostgresPool();
        } else {
            throw new IllegalArgumentException("Unsupported database kind: " + dbKind);
        }
    }

    private Pool createMySQLPool() {
        MySQLConnectOptions options = MySQLConnectOptions.fromUri(url)
            .setUser(username)
            .setPassword(password);

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(maxSize);

        return MySQLPool.pool(vertx, options, poolOptions);
    }

    private Pool createPostgresPool() {
        PgConnectOptions options = PgConnectOptions.fromUri(url)
            .setUser(username)
            .setPassword(password);

        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(maxSize);

        return PgPool.pool(vertx, options, poolOptions);
    }
}
