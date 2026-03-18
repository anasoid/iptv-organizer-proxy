package org.anasoid.iptvorganizer.utils.db;

/**
 * Enum representing supported database vendors.
 *
 * <p>Used for database-agnostic JDBC streaming configuration. Each vendor has different streaming
 * requirements and optimizations.
 */
public enum DatabaseVendor {
  /** MySQL - supports row-by-row streaming with Integer.MIN_VALUE fetch size */
  MYSQL,

  /** PostgreSQL - requires positive fetch size + autoCommit=false for streaming */
  POSTGRESQL,

  /** SQLite - doesn't buffer results, ignores fetch size */
  SQLITE,

  /** Unknown/unsupported vendor - uses conservative defaults */
  UNKNOWN
}
