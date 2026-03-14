package org.anasoid.iptvorganizer.utils.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for database-agnostic JDBC operations.
 *
 * <p>Provides vendor detection and streaming configuration that works across MySQL, PostgreSQL,
 * SQLite, and H2 databases. Each database has different optimal streaming strategies:
 *
 * <ul>
 *   <li><b>MySQL</b>: setFetchSize(Integer.MIN_VALUE) enables row-by-row streaming
 *   <li><b>PostgreSQL</b>: Requires positive fetch size + autoCommit=false for streaming
 *   <li><b>SQLite</b>: Doesn't buffer results, ignores fetch size - no special config needed
 *   <li><b>H2</b>: Test database with small datasets - uses sensible defaults
 * </ul>
 *
 * <p>The detected vendor is cached after first detection to avoid repeated DatabaseMetaData calls.
 */
@Slf4j
public class DatabaseUtils {

  private static DatabaseVendor cachedVendor;

  private DatabaseUtils() {
    // Utility class, no instantiation
  }

  /**
   * Configure a prepared statement for streaming based on the database vendor.
   *
   * <p>This method applies vendor-specific streaming optimizations to ensure efficient row-by-row
   * processing without loading all results into memory. It MUST be called before executeQuery().
   *
   * @param stmt The prepared statement to configure (must not have been executed yet)
   * @param conn The database connection
   * @throws SQLException If configuration fails
   */
  public static void configureStreamingStatement(PreparedStatement stmt, Connection conn)
      throws SQLException {
    DatabaseVendor vendor = detectVendor(conn);

    switch (vendor) {
      case MYSQL:
        // MySQL: Use Integer.MIN_VALUE for row-by-row streaming without buffering
        stmt.setFetchSize(Integer.MIN_VALUE);
        log.debug("Configured MySQL streaming: setFetchSize(Integer.MIN_VALUE)");
        break;

      case POSTGRESQL:
        // PostgreSQL: Requires positive fetch size + autoCommit=false for server-side cursor
        // This creates a server-side cursor that streams rows one at a time
        conn.setAutoCommit(false);
        stmt.setFetchSize(100);
        log.debug("Configured PostgreSQL streaming: setFetchSize(100) + autoCommit(false)");
        break;

      case SQLITE:
        // SQLite: Doesn't support row-by-row streaming, returns all results
        // No special configuration needed, fetch size is ignored
        log.debug("SQLite doesn't require special streaming configuration");
        break;

      case H2:
        // H2: Test database with small datasets, uses sensible defaults
        // No special configuration needed
        log.debug("H2 database configured with default streaming behavior");
        break;

      case UNKNOWN:
        // Conservative default: Use small positive fetch size
        // This prevents memory issues on unknown databases while staying compatible
        stmt.setFetchSize(100);
        log.warn(
            "Unknown database vendor detected, using conservative fetch size of 100. "
                + "This may not be optimal for streaming. "
                + "Consider adding support for this database.");
        break;
    }
  }

  /**
   * Detect the database vendor from the connection metadata.
   *
   * <p>The detected vendor is cached after first call to avoid repeated DatabaseMetaData lookups.
   * Use {@link #clearVendorCache()} to reset the cache if needed (primarily for testing).
   *
   * @param conn The database connection
   * @return The detected DatabaseVendor
   */
  public static DatabaseVendor detectVendor(Connection conn) {
    // Return cached vendor if already detected
    if (cachedVendor != null) {
      return cachedVendor;
    }

    try {
      DatabaseMetaData metadata = conn.getMetaData();
      String productName = metadata.getDatabaseProductName().toLowerCase();

      DatabaseVendor vendor;
      if (productName.contains("mysql")) {
        vendor = DatabaseVendor.MYSQL;
      } else if (productName.contains("postgresql")) {
        vendor = DatabaseVendor.POSTGRESQL;
      } else if (productName.contains("sqlite")) {
        vendor = DatabaseVendor.SQLITE;
      } else if (productName.contains("h2")) {
        vendor = DatabaseVendor.H2;
      } else {
        vendor = DatabaseVendor.UNKNOWN;
      }

      // Cache the detected vendor for future use
      cachedVendor = vendor;
      log.debug("Detected and cached database vendor: {} (product name: {})", vendor, productName);
      return vendor;

    } catch (SQLException e) {
      log.error("Failed to detect database vendor from connection metadata", e);
      cachedVendor = DatabaseVendor.UNKNOWN;
      return DatabaseVendor.UNKNOWN;
    }
  }

  /**
   * Clear the cached database vendor. Primarily for testing purposes.
   *
   * <p>Use this method in test setup to reset the vendor cache and allow detection of different
   * databases in different tests.
   */
  public static void clearVendorCache() {
    cachedVendor = null;
    log.debug("Cleared cached database vendor");
  }
}
