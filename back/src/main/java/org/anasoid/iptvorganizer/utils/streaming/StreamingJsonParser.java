package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.StreamingException;

@ApplicationScoped
@Slf4j
public class StreamingJsonParser {

  private static final int GC_THRESHOLD = 1000;

  @Inject ObjectMapper objectMapper;

  /**
   * Parse JSON array from input stream using lazy Iterator.
   *
   * <p>Returns JsonStreamResult with Iterator for true streaming - items are only parsed when
   * requested via Iterator.next(). Memory profile: O(1) constant memory usage regardless of total
   * array size. Try-with-resources should be used to ensure proper resource cleanup.
   *
   * @param inputStream The input stream to parse
   * @param targetClass The class to deserialize each array element into
   * @param <T> The type of items being parsed
   * @return JsonStreamResult containing Iterator and metadata (bytes read)
   */
  public <T> JsonStreamResult<T> parseJsonArray(InputStream inputStream, Class<T> targetClass) {
    // Wrap with counting stream to track bytes read
    CountingInputStream countingStream = new CountingInputStream(inputStream);

    try {
      JsonFactory factory = objectMapper.getFactory();
      JsonParser parser = factory.createParser(countingStream);

      // Verify we have a JSON array
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        parser.close();
        throw new StreamingException("Expected JSON array at start of stream");
      }

      // Create lazy iterator
      Iterator<T> iterator =
          new Iterator<T>() {
            private T nextItem = null;
            private boolean hasNextCached = false;
            private boolean finished = false;
            private int count = 0;
            private long startStep = System.currentTimeMillis();
            private long previousByteOffset = 0;

            @Override
            public boolean hasNext() {
              if (hasNextCached) {
                return true; // Already cached
              }
              if (finished) {
                return false; // Already finished
              }

              try {
                JsonToken token = parser.nextToken();
                if (token == JsonToken.END_ARRAY) {
                  finished = true;
                  parser.close();
                  log.info(
                      "Stream completed: {} items, {} bytes read",
                      count,
                      countingStream.getBytesRead());
                  return false;
                }

                nextItem = objectMapper.readValue(parser, targetClass);
                count++;
                log.debug("Parsed item: {}", nextItem.getClass().getName());

                // Log progress every 1000 items
                if (count % 1000 == 0) {
                  long location = parser.currentLocation().getByteOffset();
                  long bytesRead = location - previousByteOffset;
                  previousByteOffset = location;
                  long duration = System.currentTimeMillis() - startStep;
                  startStep = System.currentTimeMillis();
                  log.debug(
                      "Parsed item count: "
                          + count
                          + " location bytes: "
                          + (location / 1000)
                          + "k Elapsed time (ms): "
                          + (duration)
                          + " Read KB/s: "
                          + (bytesRead / (duration == 0 ? 1 : duration)));

                  System.gc();
                }

                hasNextCached = true;
                return true;
              } catch (IOException e) {
                throw new StreamingException("Failed to parse next item", e);
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
          };

      return new JsonStreamResult<>(iterator, countingStream.getBytesReadAtomic(), parser);

    } catch (IOException e) {
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
        log.info("Successfully parsed {} JSON nodes from array", itemCount);
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
