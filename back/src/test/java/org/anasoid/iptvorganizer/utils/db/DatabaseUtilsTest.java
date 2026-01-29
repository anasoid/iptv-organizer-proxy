package org.anasoid.iptvorganizer.utils.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DatabaseUtilsTest {

  @AfterEach
  void tearDown() {
    // Clear vendor cache after each test to avoid test interdependencies
    DatabaseUtils.clearVendorCache();
  }

  @Test
  void testDetectMysqlVendor() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("MySQL");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.MYSQL, vendor);
  }

  @Test
  void testDetectPostgresqlVendor() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("PostgreSQL");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.POSTGRESQL, vendor);
  }

  @Test
  void testDetectSqliteVendor() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("SQLite");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.SQLITE, vendor);
  }

  @Test
  void testDetectH2Vendor() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("H2");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.H2, vendor);
  }

  @Test
  void testDetectUnknownVendor() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("UnknownDatabase 1.0");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.UNKNOWN, vendor);
  }

  @Test
  void testDetectVendorCaseInsensitive() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("MYSQL");

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    assertEquals(DatabaseVendor.MYSQL, vendor);
  }

  @Test
  void testDetectVendorCaching() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("MySQL");

    // First call should query metadata
    DatabaseVendor vendor1 = DatabaseUtils.detectVendor(mockConnection);
    assertEquals(DatabaseVendor.MYSQL, vendor1);

    // Second call should use cache (no new metadata query)
    DatabaseVendor vendor2 = DatabaseUtils.detectVendor(mockConnection);
    assertEquals(DatabaseVendor.MYSQL, vendor2);

    // Verify metadata was only called once
    verify(mockConnection, times(1)).getMetaData();
  }

  @Test
  void testClearVendorCache() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("MySQL");

    // First detection
    DatabaseUtils.detectVendor(mockConnection);

    // Clear cache
    DatabaseUtils.clearVendorCache();

    // Reset mock for second detection
    reset(mockConnection, mockMetadata);
    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("PostgreSQL");

    // Should query metadata again after cache clear
    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);
    assertEquals(DatabaseVendor.POSTGRESQL, vendor);
  }

  @Test
  void testConfigureStreamingStatementMysql() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);

    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("MySQL");

    DatabaseUtils.configureStreamingStatement(mockStatement, mockConnection);

    // Verify Integer.MIN_VALUE is set for MySQL
    verify(mockStatement).setFetchSize(Integer.MIN_VALUE);
  }

  @Test
  void testConfigureStreamingStatementPostgresql() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);

    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("PostgreSQL");

    DatabaseUtils.configureStreamingStatement(mockStatement, mockConnection);

    // Verify autoCommit is set to false and fetch size is 100 for PostgreSQL
    verify(mockConnection).setAutoCommit(false);
    verify(mockStatement).setFetchSize(100);
  }

  @Test
  void testConfigureStreamingStatementSqlite() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);

    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("SQLite");

    DatabaseUtils.configureStreamingStatement(mockStatement, mockConnection);

    // SQLite should have no special configuration
    verify(mockStatement, never()).setFetchSize(anyInt());
    verify(mockConnection, never()).setAutoCommit(anyBoolean());
  }

  @Test
  void testConfigureStreamingStatementH2() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);

    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("H2");

    DatabaseUtils.configureStreamingStatement(mockStatement, mockConnection);

    // H2 should have no special configuration
    verify(mockStatement, never()).setFetchSize(anyInt());
    verify(mockConnection, never()).setAutoCommit(anyBoolean());
  }

  @Test
  void testConfigureStreamingStatementUnknown() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    DatabaseMetaData mockMetadata = mock(DatabaseMetaData.class);

    when(mockConnection.getMetaData()).thenReturn(mockMetadata);
    when(mockMetadata.getDatabaseProductName()).thenReturn("UnknownDatabase");

    DatabaseUtils.configureStreamingStatement(mockStatement, mockConnection);

    // Unknown vendor should use conservative default of 100
    verify(mockStatement).setFetchSize(100);
  }

  @Test
  void testDetectVendorHandlesSqlException() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    when(mockConnection.getMetaData()).thenThrow(new SQLException("Connection failed"));

    DatabaseVendor vendor = DatabaseUtils.detectVendor(mockConnection);

    // Should return UNKNOWN when SQLException occurs
    assertEquals(DatabaseVendor.UNKNOWN, vendor);
  }

  @Test
  void testConfigureStreamingStatementUsesCache() throws SQLException {
    Connection mockConnection1 = mock(Connection.class);
    DatabaseMetaData mockMetadata1 = mock(DatabaseMetaData.class);
    when(mockConnection1.getMetaData()).thenReturn(mockMetadata1);
    when(mockMetadata1.getDatabaseProductName()).thenReturn("MySQL");

    // First statement configuration - caches MySQL
    PreparedStatement mockStatement1 = mock(PreparedStatement.class);
    DatabaseUtils.configureStreamingStatement(mockStatement1, mockConnection1);
    verify(mockStatement1).setFetchSize(Integer.MIN_VALUE);

    // Second configuration with different connection should use cached vendor (MySQL)
    Connection mockConnection2 = mock(Connection.class);
    PreparedStatement mockStatement2 = mock(PreparedStatement.class);
    DatabaseUtils.configureStreamingStatement(mockStatement2, mockConnection2);

    // Should still be MySQL from cache, so should call setFetchSize(Integer.MIN_VALUE)
    verify(mockStatement2).setFetchSize(Integer.MIN_VALUE);
    // Second connection metadata should not be called due to caching
    verify(mockConnection2, never()).getMetaData();
  }
}
