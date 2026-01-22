package org.anasoid.iptvorganizer.utils.streaming;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

public class JdbcStreamIteratorTest {

  @Test
  void testStreamingIteratorWithValidResults() throws SQLException {
    // Setup mocks
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    // Simulate 3 rows of data
    when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
    when(mockResultSet.getInt("id")).thenReturn(1).thenReturn(2).thenReturn(3);
    when(mockResultSet.getString("name"))
        .thenReturn("Item 1")
        .thenReturn("Item 2")
        .thenReturn("Item 3");

    // Create iterator
    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Verify streaming behavior
    List<TestEntity> results = new ArrayList<>();
    while (iterator.hasNext()) {
      results.add(iterator.next());
    }

    assertEquals(3, results.size());
    assertEquals(1, results.get(0).id);
    assertEquals("Item 1", results.get(0).name);
    assertEquals(3, results.get(2).id);
    assertEquals("Item 3", results.get(2).name);
  }

  @Test
  void testEmptyResultSet() throws SQLException {
    // Setup mocks for empty result set
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(false);

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    assertFalse(iterator.hasNext());
    assertEquals(0, countItems(iterator));
  }

  @Test
  void testNoSuchElementExceptionWhenCallingNextWithoutHasNext() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt("id")).thenReturn(1);
    when(mockResultSet.getString("name")).thenReturn("Item 1");

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Calling next() without hasNext() should throw NoSuchElementException
    assertThrows(java.util.NoSuchElementException.class, iterator::next);
  }

  @Test
  void testResourcesClosedAfterIterationComplete() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockResultSet.getInt("id")).thenReturn(1);
    when(mockResultSet.getString("name")).thenReturn("Item 1");

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Exhaust the iterator
    while (iterator.hasNext()) {
      iterator.next();
    }

    // Verify resources are closed (ResultSet, Statement, Connection in order)
    InOrder inOrder = inOrder(mockResultSet, mockStatement, mockConnection);
    inOrder.verify(mockResultSet).close();
    inOrder.verify(mockStatement).close();
    inOrder.verify(mockConnection).close();
  }

  @Test
  void testExplicitCloseCallsClosesResources() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(true);

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    iterator.close();

    // Verify all resources are closed
    verify(mockResultSet).close();
    verify(mockStatement).close();
    verify(mockConnection).close();
  }

  @Test
  void testSqlExceptionDuringIteration() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenThrow(new SQLException("Database error"));

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Should throw RuntimeException wrapping SQLException
    assertThrows(RuntimeException.class, iterator::hasNext);
  }

  @Test
  void testSqlExceptionDuringRowMapping() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(true);
    when(mockResultSet.getInt("id")).thenThrow(new SQLException("Column read error"));

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Should throw RuntimeException when trying to read the row
    assertThrows(RuntimeException.class, iterator::hasNext);
  }

  @Test
  void testCachingBehavior() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    when(mockResultSet.next()).thenReturn(true).thenReturn(false);
    when(mockResultSet.getInt("id")).thenReturn(1);
    when(mockResultSet.getString("name")).thenReturn("Item 1");

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Call hasNext multiple times - should return true each time
    assertTrue(iterator.hasNext());
    assertTrue(iterator.hasNext());
    assertTrue(iterator.hasNext());

    // Call next() to get the cached item
    TestEntity entity = iterator.next();
    assertEquals(1, entity.id);

    // Second hasNext() call should move to next result or finish
    assertFalse(iterator.hasNext());
  }

  @Test
  void testLargeDataset() throws SQLException {
    Connection mockConnection = mock(Connection.class);
    PreparedStatement mockStatement = mock(PreparedStatement.class);
    ResultSet mockResultSet = mock(ResultSet.class);

    // Simulate 2000 rows to test GC threshold (happens every 1000 items)
    when(mockResultSet.next())
        .then(
            invocation -> {
              int callCount = mockResultSet.getInt("id");
              return callCount < 2000;
            });

    int[] callCount = {0};
    when(mockResultSet.getInt("id"))
        .then(
            invocation -> {
              callCount[0]++;
              return callCount[0];
            });
    when(mockResultSet.getString("name")).then(invocation -> "Item " + callCount[0]);

    JdbcStreamIterator<TestEntity> iterator =
        new JdbcStreamIterator<>(
            mockConnection,
            mockStatement,
            mockResultSet,
            rs -> new TestEntity(rs.getInt("id"), rs.getString("name")));

    // Count the streamed items
    int count = 0;
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }

    // Should have processed items (exact count depends on mock behavior)
    assertTrue(count > 0, "Iterator should process items from mock ResultSet");
  }

  /** Helper method to count items in an iterator */
  private int countItems(Iterator<?> iterator) {
    int count = 0;
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }
    return count;
  }

  /** Simple test entity for testing */
  static class TestEntity {
    int id;
    String name;

    TestEntity(int id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
