package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.java.Log;
import org.anasoid.iptvorganizer.exceptions.StreamingException;

@ApplicationScoped
@Log
public class StreamingJsonParser {

  private static final int GC_THRESHOLD = 1000;

  @Inject ObjectMapper objectMapper;

  /**
   * Parse JSON array from input stream and return items as List<T>.
   *
   * <p>Memory profile: O(1) per item - parses one object at a time and adds to result list
   */
  public <T> List<T> parseJsonArray(InputStream inputStream, Class<T> targetClass) {
    List<T> results = new ArrayList<>();
    int itemCount = 0;
    long startTime = System.currentTimeMillis();
    long previousByteOffset = 0;

    try {
      JsonFactory factory = objectMapper.getFactory();
      JsonParser parser = factory.createParser(inputStream);

      // Verify we have a JSON array
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        parser.close();
        throw new StreamingException("Expected JSON array at start of stream");
      }

      // Parse items one by one
      while (parser.nextToken() != JsonToken.END_ARRAY) {
        T item = objectMapper.readValue(parser, targetClass);
        results.add(item);
        log.fine(() -> "Parsed item: " + item.getClass().getName());

        // Trigger explicit GC every 1000 items
        itemCount++;
        if (itemCount % 1000 == 0) {
          long location = parser.currentLocation().getByteOffset();
          long bytesRead = location - previousByteOffset;
          previousByteOffset = location;
          long duration = System.currentTimeMillis() - startTime;
          log.info(
              "Parsed item count: "
                  + itemCount
                  + " location bytes: "
                  + (location / 1000)
                  + "k Elapsed time (s): "
                  + (duration / 1000)
                  + " Read KB/s: "
                  + (bytesRead / (duration == 0 ? 1 : duration)));
          startTime = System.currentTimeMillis();
          System.gc();
        }
      }

      parser.close();
      log.info("Successfully parsed " + itemCount + " items from JSON array");
      return results;

    } catch (Exception e) {
      throw new StreamingException("JSON parsing failed", e);
    }
  }

  /**
   * Parse JSON stream (array or single object) and return as List of JsonNodes.
   *
   * <p>More flexible than parseJsonArray - handles various JSON structures
   */
  public List<JsonNode> parseJsonStream(InputStream inputStream) {
    List<JsonNode> results = new ArrayList<>();
    int itemCount = 0;

    try {
      JsonFactory factory = objectMapper.getFactory();
      JsonParser parser = factory.createParser(inputStream);

      JsonToken token = parser.nextToken();

      // Handle JSON array
      if (token == JsonToken.START_ARRAY) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
          JsonNode node = objectMapper.readTree(parser);
          results.add(node);

          // Trigger explicit GC every 1000 items
          itemCount++;
          if (itemCount % GC_THRESHOLD == 0) {
            System.gc();
          }
        }
        parser.close();
        log.info("Successfully parsed " + itemCount + " JSON nodes from array");
        return results;
      }
      // Handle single JSON object
      else if (token == JsonToken.START_OBJECT) {
        JsonNode node = objectMapper.readTree(parser);
        parser.close();
        results.add(node);
        return results;
      }
      // Handle other cases (null, primitives, etc.)
      else {
        parser.close();
        throw new StreamingException(
            "Unexpected JSON token: " + token + ". Expected array or object");
      }

    } catch (Exception e) {
      throw new StreamingException("JSON stream parsing failed", e);
    }
  }
}
