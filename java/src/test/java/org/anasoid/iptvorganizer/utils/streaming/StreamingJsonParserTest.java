package org.anasoid.iptvorganizer.utils.streaming;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StreamingJsonParserTest {

  private StreamingJsonParser jsonParser;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    jsonParser = new StreamingJsonParser();
    // Set the ObjectMapper field via reflection since it's @Inject
    try {
      var field = StreamingJsonParser.class.getDeclaredField("objectMapper");
      field.setAccessible(true);
      field.set(jsonParser, objectMapper);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testParseJsonArray() throws IOException {
    String json =
        """
        [
            {"id": 1, "name": "Item 1"},
            {"id": 2, "name": "Item 2"},
            {"id": 3, "name": "Item 3"}
        ]
        """;

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    // Use the new streaming API with try-with-resources
    try (JsonStreamResult<JsonNode> streamResult =
        jsonParser.parseJsonArray(inputStream, JsonNode.class)) {
      List<JsonNode> results = new ArrayList<>();
      Iterator<JsonNode> iterator = streamResult.iterator();
      while (iterator.hasNext()) {
        results.add(iterator.next());
      }

      assertEquals(3, results.size());
      assertEquals(1, results.get(0).get("id").asInt());
      assertEquals("Item 1", results.get(0).get("name").asText());
      assertEquals(3, results.get(2).get("id").asInt());
    }
  }

  @Test
  void testParseJsonArrayWithTypedClass() throws IOException {
    String json =
        """
        [
            {"id": 1, "name": "Stream 1"},
            {"id": 2, "name": "Stream 2"}
        ]
        """;

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    // Use the new streaming API with try-with-resources
    try (JsonStreamResult<TestItem> streamResult =
        jsonParser.parseJsonArray(inputStream, TestItem.class)) {
      List<TestItem> results = new ArrayList<>();
      Iterator<TestItem> iterator = streamResult.iterator();
      while (iterator.hasNext()) {
        results.add(iterator.next());
      }

      assertEquals(2, results.size());
      assertEquals(1, results.get(0).id);
      assertEquals("Stream 1", results.get(0).name);
    }
  }

  @Test
  void testParseJsonStream_Array() {
    String json =
        """
        [
            {"id": 1, "name": "Item 1"},
            {"id": 2, "name": "Item 2"}
        ]
        """;

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    List<JsonNode> results = jsonParser.parseJsonStream(inputStream);

    assertEquals(2, results.size());
    assertEquals(1, results.get(0).get("id").asInt());
  }

  @Test
  void testParseJsonStream_SingleObject() {
    String json =
        """
        {"id": 1, "name": "Item 1"}
        """;

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    List<JsonNode> results = jsonParser.parseJsonStream(inputStream);

    assertEquals(1, results.size());
    assertEquals(1, results.get(0).get("id").asInt());
  }

  @Test
  void testParseJsonArrayEmptyArray() throws IOException {
    String json = "[]";

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    // Use the new streaming API with try-with-resources
    try (JsonStreamResult<JsonNode> streamResult =
        jsonParser.parseJsonArray(inputStream, JsonNode.class)) {
      List<JsonNode> results = new ArrayList<>();
      Iterator<JsonNode> iterator = streamResult.iterator();
      while (iterator.hasNext()) {
        results.add(iterator.next());
      }

      assertEquals(0, results.size());
    }
  }

  @Test
  void testParseJsonArrayInvalidJson() {
    String json = "not json at all";

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    assertThrows(
        Exception.class,
        () -> {
          try (JsonStreamResult<JsonNode> streamResult =
              jsonParser.parseJsonArray(inputStream, JsonNode.class)) {
            Iterator<JsonNode> iterator = streamResult.iterator();
            if (iterator.hasNext()) {
              iterator.next();
            }
          }
        });
  }

  @Test
  void testParseJsonArrayNotArray() {
    String json =
        """
        {"id": 1, "name": "Item 1"}
        """;

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    assertThrows(
        Exception.class,
        () -> {
          try (JsonStreamResult<JsonNode> streamResult =
              jsonParser.parseJsonArray(inputStream, JsonNode.class)) {
            // Exception should be thrown on first hasNext() call
            streamResult.iterator().hasNext();
          }
        });
  }

  @Test
  void testParseJsonStream_InvalidToken() {
    String json = "123";

    InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

    assertThrows(
        Exception.class,
        () -> {
          jsonParser.parseJsonStream(inputStream);
        });
  }

  @Test
  void testParseJsonArrayLargeArray() throws IOException {
    // Create a large JSON array with 1000 items
    StringBuilder jsonBuilder = new StringBuilder("[");
    for (int i = 1; i <= 1000; i++) {
      if (i > 1) jsonBuilder.append(",");
      jsonBuilder
          .append("{\"id\": ")
          .append(i)
          .append(", \"name\": \"Item ")
          .append(i)
          .append("\"}");
    }
    jsonBuilder.append("]");

    InputStream inputStream =
        new ByteArrayInputStream(jsonBuilder.toString().getBytes(StandardCharsets.UTF_8));

    // Use the new streaming API - demonstrating lazy evaluation
    try (JsonStreamResult<JsonNode> streamResult =
        jsonParser.parseJsonArray(inputStream, JsonNode.class)) {
      List<JsonNode> results = new ArrayList<>();
      Iterator<JsonNode> iterator = streamResult.iterator();

      int count = 0;
      // Only collect first, middle, and last items for verification
      // This demonstrates that we don't need to hold all items in memory
      JsonNode firstItem = null;
      JsonNode lastItem = null;

      while (iterator.hasNext()) {
        JsonNode item = iterator.next();
        count++;

        if (count == 1) {
          firstItem = item;
          results.add(item);
        } else if (count == 1000) {
          lastItem = item;
          results.add(item);
        }
      }

      // Verify count and values
      assertEquals(1000, count);
      assertEquals(1, firstItem.get("id").asInt());
      assertEquals(1000, lastItem.get("id").asInt());
      assertEquals(2, results.size()); // We only stored first and last
    }
  }

  // Test POJO for typed parsing
  public static class TestItem {
    public int id;
    public String name;
  }
}
