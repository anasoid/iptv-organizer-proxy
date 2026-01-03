package org.anasoid.iptvorganizer.services.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class StreamingJsonParserTest {

    @Inject
    StreamingJsonParser jsonParser;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testParseJsonArray() {
        String json = """
            [
                {"id": 1, "name": "Item 1"},
                {"id": 2, "name": "Item 2"},
                {"id": 3, "name": "Item 3"}
            ]
            """;

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        List<JsonNode> results = jsonParser.parseJsonArray(inputStream, JsonNode.class)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(3, results.size());
        assertEquals(1, results.get(0).get("id").asInt());
        assertEquals("Item 1", results.get(0).get("name").asText());
        assertEquals(3, results.get(2).get("id").asInt());
    }

    @Test
    void testParseJsonArrayWithTypedClass() {
        String json = """
            [
                {"id": 1, "name": "Stream 1"},
                {"id": 2, "name": "Stream 2"}
            ]
            """;

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        List<TestItem> results = jsonParser.parseJsonArray(inputStream, TestItem.class)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).id);
        assertEquals("Stream 1", results.get(0).name);
    }

    @Test
    void testParseJsonStream_Array() {
        String json = """
            [
                {"id": 1, "name": "Item 1"},
                {"id": 2, "name": "Item 2"}
            ]
            """;

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        List<JsonNode> results = jsonParser.parseJsonStream(inputStream)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).get("id").asInt());
    }

    @Test
    void testParseJsonStream_SingleObject() {
        String json = """
            {"id": 1, "name": "Item 1"}
            """;

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        List<JsonNode> results = jsonParser.parseJsonStream(inputStream)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).get("id").asInt());
    }

    @Test
    void testParseJsonArrayEmptyArray() {
        String json = "[]";

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        List<JsonNode> results = jsonParser.parseJsonArray(inputStream, JsonNode.class)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(0, results.size());
    }

    @Test
    void testParseJsonArrayInvalidJson() {
        String json = "not json at all";

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> {
            jsonParser.parseJsonArray(inputStream, JsonNode.class)
                .collect()
                .asList()
                .await()
                .indefinitely();
        });
    }

    @Test
    void testParseJsonArrayNotArray() {
        String json = """
            {"id": 1, "name": "Item 1"}
            """;

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> {
            jsonParser.parseJsonArray(inputStream, JsonNode.class)
                .collect()
                .asList()
                .await()
                .indefinitely();
        });
    }

    @Test
    void testParseJsonStream_InvalidToken() {
        String json = "123";

        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> {
            jsonParser.parseJsonStream(inputStream)
                .collect()
                .asList()
                .await()
                .indefinitely();
        });
    }

    @Test
    void testParseJsonArrayLargeArray() {
        // Create a large JSON array with 1000 items
        StringBuilder jsonBuilder = new StringBuilder("[");
        for (int i = 1; i <= 1000; i++) {
            if (i > 1) jsonBuilder.append(",");
            jsonBuilder.append("{\"id\": ").append(i).append(", \"name\": \"Item ").append(i).append("\"}");
        }
        jsonBuilder.append("]");

        InputStream inputStream = new ByteArrayInputStream(jsonBuilder.toString().getBytes(StandardCharsets.UTF_8));

        List<JsonNode> results = jsonParser.parseJsonArray(inputStream, JsonNode.class)
            .collect()
            .asList()
            .await()
            .indefinitely();

        assertEquals(1000, results.size());
        assertEquals(1, results.get(0).get("id").asInt());
        assertEquals(1000, results.get(999).get("id").asInt());
    }

    // Test POJO for typed parsing
    public static class TestItem {
        public int id;
        public String name;
    }
}
