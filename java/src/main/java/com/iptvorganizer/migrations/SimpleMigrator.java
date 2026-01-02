package com.iptvorganizer.migrations;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class SimpleMigrator {
    private static final Logger LOG = Logger.getLogger(SimpleMigrator.class);

    @Inject
    Pool client;

    @Inject
    @ConfigProperty(name = "quarkus.datasource.db-kind")
    String dbKind;

    // Hardcoded migration list for native image compatibility
    // Updated as new migrations are added
    private static final List<String> MIGRATIONS = List.of(
        "V001__create_schema_version.sql"
    );

    void onStart(@Observes StartupEvent event) {
        LOG.info("Starting database migrations for: " + dbKind);
        runMigrations()
            .await()
            .indefinitely();
    }

    private Uni<Void> runMigrations() {
        return ensureSchemaVersionTable()
            .flatMap(v -> getAppliedMigrations())
            .flatMap(appliedVersions -> {
                List<String> pendingMigrations = new ArrayList<>();
                for (String migration : MIGRATIONS) {
                    String version = getVersion(migration);
                    if (!appliedVersions.contains(version)) {
                        pendingMigrations.add(migration);
                    }
                }

                if (pendingMigrations.isEmpty()) {
                    LOG.info("No pending migrations");
                    return Uni.createFrom().voidItem();
                }

                List<Uni<Void>> migrationUnis = new ArrayList<>();
                for (String migration : pendingMigrations) {
                    migrationUnis.add(applyMigration(migration));
                }

                return Uni.join().all(migrationUnis).andFailFast().replaceWithVoid();
            });
    }

    private Uni<Void> ensureSchemaVersionTable() {
        String createTableSql;
        if ("sqlite".equalsIgnoreCase(dbKind)) {
            createTableSql = """
                CREATE TABLE IF NOT EXISTS schema_version (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    version TEXT NOT NULL UNIQUE,
                    description TEXT,
                    checksum TEXT NOT NULL,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        } else {
            // MySQL and PostgreSQL
            createTableSql = """
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

        return client.query(createTableSql)
            .execute()
            .invoke(() -> LOG.debug("schema_version table ensured"))
            .replaceWithVoid();
    }

    private Uni<List<String>> getAppliedMigrations() {
        return client.query("SELECT version FROM schema_version")
            .execute()
            .map(rowSet -> {
                List<String> versions = new ArrayList<>();
                for (Row row : rowSet) {
                    versions.add(row.getString("version"));
                }
                return versions;
            })
            .onFailure(throwable -> {
                LOG.debug("schema_version table does not exist yet, treating as empty");
                return true;
            })
            .recoverWithItem(new ArrayList<>());
    }

    private Uni<Void> applyMigration(String filename) {
        String version = getVersion(filename);
        String description = getDescription(filename);

        return Uni.createFrom().item(() -> {
            String sql = loadSqlFile(filename);
            return calculateChecksum(sql);
        }).flatMap(checksum -> {
            String sql = loadSqlFile(filename);

            return client.withTransaction(conn -> {
                return conn.query(sql)
                    .execute()
                    .flatMap(rs -> {
                        String insertSql = "INSERT INTO schema_version (version, description, checksum) VALUES (?, ?, ?)";
                        return conn.preparedQuery(insertSql)
                            .execute(Tuple.of(version, description, checksum));
                    })
                    .invoke(() -> LOG.info("Applied migration: " + filename))
                    .replaceWithVoid();
            });
        });
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
