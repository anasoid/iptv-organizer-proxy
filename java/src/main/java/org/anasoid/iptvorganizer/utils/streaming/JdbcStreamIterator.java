package org.anasoid.iptvorganizer.utils.streaming;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;

/**
 * Lazy Iterator for streaming database ResultSet rows as entities.
 *
 * <p>Enables O(1) constant memory usage by processing rows one at a time from the database instead
 * of loading all into memory. Follows the pattern established by StreamingJsonParser for consistent
 * caching and resource management.
 *
 * <p>This iterator works across multiple database vendors (MySQL, PostgreSQL, SQLite, H2) when
 * used with {@link org.anasoid.iptvorganizer.utils.db.DatabaseUtils#configureStreamingStatement}
 * for database-agnostic streaming configuration.
 *
 * <p>Usage:
 *
 * <pre>
 * Connection conn = dataSource.getConnection();
 * PreparedStatement stmt = conn.prepareStatement(sql,
 *     ResultSet.TYPE_FORWARD_ONLY,
 *     ResultSet.CONCUR_READ_ONLY);
 * DatabaseUtils.configureStreamingStatement(stmt, conn);
 * stmt.setLong(1, value);
 * ResultSet rs = stmt.executeQuery();
 * Iterator<MyEntity> iterator = new JdbcStreamIterator<>(
 *   conn, stmt, rs, (resultSet) -> mapRow(resultSet)
 * );
 * try {
 *   while (iterator.hasNext()) {
 *     MyEntity entity = iterator.next();
 *     // process entity
 *   }
 * } finally {
 *   iterator.close(); // Auto-closes connection, statement, result set
 * }
 * </pre>
 *
 * @param <T> The type of entities being streamed
 */
@Slf4j
public class JdbcStreamIterator<T> implements Iterator<T>, Closeable {

  private static final int GC_THRESHOLD = 1000;

  private final Connection connection;
  private final PreparedStatement statement;
  private final ResultSet resultSet;
  private final RowMapper<T> rowMapper;

  private T nextItem = null;
  private boolean hasNextCached = false;
  private boolean finished = false;
  private int count = 0;
  private long startTime = System.currentTimeMillis();
  private long previousCount = 0;

  /**
   * Create a streaming iterator from a JDBC ResultSet.
   *
   * <p>The iterator manages the lifecycle of the provided connection, statement, and result set.
   * They will be automatically closed when iteration completes or when close() is explicitly
   * called.
   *
   * <p>Requirements for proper streaming (O(1) memory):
   *
   * <ul>
   *   <li>ResultSet must be created with TYPE_FORWARD_ONLY and CONCUR_READ_ONLY
   *   <li>Statement must call {@link org.anasoid.iptvorganizer.utils.db.DatabaseUtils#configureStreamingStatement}
   *   BEFORE executeQuery() to apply database-vendor-specific streaming optimizations
   *   <li>This enables efficient row-by-row streaming across MySQL, PostgreSQL, SQLite, and H2
   * </ul>
   *
   * <p>Example:
   *
   * <pre>
   * PreparedStatement stmt = conn.prepareStatement(sql,
   *     ResultSet.TYPE_FORWARD_ONLY,
   *     ResultSet.CONCUR_READ_ONLY);
   * DatabaseUtils.configureStreamingStatement(stmt, conn);  // Must be BEFORE executeQuery()
   * stmt.setLong(1, value);
   * ResultSet rs = stmt.executeQuery();
   * Iterator<T> iter = new JdbcStreamIterator<>(conn, stmt, rs, mapper);
   * </pre>
   *
   * @param connection The database connection (will be closed when iteration ends)
   * @param statement The prepared statement (will be closed when iteration ends)
   * @param resultSet The result set (will be closed when iteration ends)
   * @param rowMapper Function to map ResultSet rows to entities
   */
  public JdbcStreamIterator(
      Connection connection,
      PreparedStatement statement,
      ResultSet resultSet,
      RowMapper<T> rowMapper) {
    this.connection = connection;
    this.statement = statement;
    this.resultSet = resultSet;
    this.rowMapper = rowMapper;
  }

  @Override
  public boolean hasNext() {
    if (hasNextCached) {
      return true; // Already cached
    }
    if (finished) {
      return false; // Already finished
    }

    try {
      if (resultSet.next()) {
        nextItem = rowMapper.mapRow(resultSet);
        count++;
        log.debug("Streamed item: {} (count: {})", nextItem.getClass().getName(), count);

        // Log progress and trigger GC every 1000 items (like StreamingJsonParser)
        if (count % GC_THRESHOLD == 0) {
          long duration = System.currentTimeMillis() - startTime;
          long itemsThisStep = count - previousCount;
          previousCount = count;
          long itemsPerSecond = itemsThisStep > 0 ? (itemsThisStep * 1000) / duration : 0;
          log.debug(
              "Stream progress: {} items read in {}ms ({}items/sec)",
              count,
              duration,
              itemsPerSecond + " items/sec)");

          System.gc();
        }

        hasNextCached = true;
        return true;
      } else {
        finished = true;
        close();
        log.info(
            "Stream completed: {} items read in {}ms",
            count,
            (System.currentTimeMillis() - startTime));
        return false;
      }
    } catch (SQLException e) {
      closeQuietly();
      throw new RuntimeException("Failed to read next row from result set", e);
    }
  }

  @Override
  public T next() {
    if (!hasNextCached) {
      throw new NoSuchElementException();
    }
    T item = nextItem;
    hasNextCached = false;
    nextItem = null; // Help GC
    return item;
  }

  @Override
  public void close() {
    try {
      // Close in reverse order: ResultSet → Statement → Connection
      if (resultSet != null) {
        try {
          resultSet.close();
        } catch (SQLException e) {
          log.warn("Failed to close ResultSet", e);
        }
      }
      if (statement != null) {
        try {
          statement.close();
        } catch (SQLException e) {
          log.warn("Failed to close PreparedStatement", e);
        }
      }
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException e) {
          log.warn("Failed to close Connection", e);
        }
      }
    } catch (Exception e) {
      log.warn("Error during resource cleanup", e);
    }
  }

  /** Close quietly without throwing exceptions (used internally for error handling) */
  private void closeQuietly() {
    try {
      close();
    } catch (Exception e) {
      log.warn("Error during quiet close", e);
    }
  }

  /**
   * Functional interface for mapping ResultSet rows to entities.
   *
   * <p>Implement this to convert a single row from the result set to your entity type.
   *
   * @param <T> The entity type
   */
  @FunctionalInterface
  public interface RowMapper<T> {
    /**
     * Map a single ResultSet row to an entity.
     *
     * @param rs The result set, positioned at the current row
     * @return The mapped entity
     * @throws SQLException If reading from result set fails
     */
    T mapRow(ResultSet rs) throws SQLException;
  }
}
