package org.anasoid.iptvorganizer.utils.streaming;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import lombok.extern.java.Log;

/**
 * Lazy Iterator for streaming database ResultSet rows as entities.
 *
 * <p>Enables O(1) constant memory usage by processing rows one at a time from the database instead
 * of loading all into memory. Follows the pattern established by StreamingJsonParser for consistent
 * caching and resource management.
 *
 * <p>Usage:
 *
 * <pre>
 * Connection conn = dataSource.getConnection();
 * PreparedStatement stmt = conn.prepareStatement(sql);
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
@Log
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
   * <p>MySQL-specific: Configures the statement with Integer.MIN_VALUE fetch size to enable
   * row-by-row streaming without client-side buffering.
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

    try {
      // CRITICAL: Configure for MySQL streaming (no client-side buffering)
      statement.setFetchSize(Integer.MIN_VALUE);
    } catch (SQLException e) {
      closeQuietly();
      throw new RuntimeException("Failed to configure JDBC streaming", e);
    }
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
        log.fine(
            () -> "Streamed item: " + nextItem.getClass().getName() + " (count: " + count + ")");

        // Log progress and trigger GC every 1000 items (like StreamingJsonParser)
        if (count % GC_THRESHOLD == 0) {
          long duration = System.currentTimeMillis() - startTime;
          long itemsThisStep = count - previousCount;
          previousCount = count;
          long itemsPerSecond = itemsThisStep > 0 ? (itemsThisStep * 1000) / duration : 0;
          log.fine(
              "Stream progress: "
                  + count
                  + " items read in "
                  + duration
                  + "ms ("
                  + itemsPerSecond
                  + " items/sec)");

          System.gc();
        }

        hasNextCached = true;
        return true;
      } else {
        finished = true;
        close();
        log.info(
            "Stream completed: "
                + count
                + " items read in "
                + (System.currentTimeMillis() - startTime)
                + "ms");
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
          log.warning("Failed to close ResultSet: " + e.getMessage());
        }
      }
      if (statement != null) {
        try {
          statement.close();
        } catch (SQLException e) {
          log.warning("Failed to close PreparedStatement: " + e.getMessage());
        }
      }
      if (connection != null) {
        try {
          connection.close();
        } catch (SQLException e) {
          log.warning("Failed to close Connection: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      log.warning("Error during resource cleanup: " + e.getMessage());
    }
  }

  /** Close quietly without throwing exceptions (used internally for error handling) */
  private void closeQuietly() {
    try {
      close();
    } catch (Exception e) {
      log.warning("Error during quiet close: " + e.getMessage());
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
