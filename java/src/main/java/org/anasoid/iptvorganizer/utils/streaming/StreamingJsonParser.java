package org.anasoid.iptvorganizer.utils.streaming;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.java.Log;
import org.anasoid.iptvorganizer.exceptions.StreamingException;

@ApplicationScoped
@Log
public class StreamingJsonParser {

  private static final int GC_THRESHOLD = 1000;

  @Inject ObjectMapper objectMapper;

  /**
   * Parse JSON array from input stream and emit items as Multi<T> with proper backpressure.
   *
   * <p>CRITICAL: Uses custom Iterable with iterator-based pull semantics. Each item is parsed
   * on-demand when downstream requests it via iterator.next(), preventing memory accumulation.
   *
   * <p>Memory profile: O(1) - only one parsed object in memory at a time (released after downstream
   * processes it)
   */
  public <T> Multi<T> parseJsonArray(InputStream inputStream, Class<T> targetClass) {
    try {
      JsonFactory factory = objectMapper.getFactory();
      JsonParser parser = factory.createParser(inputStream);

      // Verify we have a JSON array
      if (parser.nextToken() != JsonToken.START_ARRAY) {
        parser.close();
        throw new StreamingException("Expected JSON array at start of stream");
      }

      AtomicInteger itemCounter = new AtomicInteger(0);
      AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
      // Create iterable that provides pull-based iteration
      // Iterator.next() is called on-demand by downstream, providing backpressure
      Iterable<T> iterable =
          () ->
              new java.util.Iterator<T>() {
                private T nextItem = null;
                private boolean finished = false;

                @Override
                public boolean hasNext() {
                  if (finished) {
                    return false;
                  }

                  if (nextItem != null) {
                    return true;
                  }

                  try {
                    JsonToken token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY || token == null) {
                      parser.close();
                      finished = true;
                      return false;
                    }

                    nextItem = objectMapper.readValue(parser, targetClass);
                    log.fine(() -> "Reading nextItem: " + nextItem.getClass().getName());
                    // Trigger explicit GC every 1000 items
                    int count = itemCounter.incrementAndGet();
                    if (count % 1000 == 0) {
                      log.info(
                          "Reading item count: "
                              + count
                              + " location bytes "
                              + parser.currentLocation().getByteOffset()
                              + " Elapsed time (ms): "
                              + (System.currentTimeMillis() - startTime.get()));
                      startTime.set(System.currentTimeMillis());
                    }

                    return true;
                  } catch (Exception e) {
                    finished = true;
                    try {
                      parser.close();
                    } catch (Exception closeEx) {
                      // Ignore
                    }
                    throw new StreamingException(
                        "Failed to parse array element at index: " + itemCounter.get(), e);
                  }
                }

                @Override
                public T next() {
                  if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                  }
                  T result = nextItem;
                  nextItem = null;
                  return result;
                }
              };

      return Multi.createFrom().iterable(iterable);

    } catch (Exception e) {
      return Multi.createFrom().failure(new StreamingException("JSON parsing failed", e));
    }
  }

  /**
   * Parse JSON stream (array or single object) and emit as JsonNode with proper backpressure.
   *
   * <p>More flexible than parseJsonArray - handles various JSON structures
   *
   * <p>CRITICAL: Respects backpressure from downstream consumers to prevent memory accumulation
   */
  public Multi<JsonNode> parseJsonStream(InputStream inputStream) {
    try {
      JsonFactory factory = objectMapper.getFactory();
      JsonParser parser = factory.createParser(inputStream);

      JsonToken token = parser.nextToken();

      // Handle JSON array with pull-based iterator
      if (token == JsonToken.START_ARRAY) {
        AtomicInteger itemCounter = new AtomicInteger(0);

        Iterable<JsonNode> iterable =
            () ->
                new java.util.Iterator<JsonNode>() {
                  private JsonNode nextItem = null;
                  private boolean finished = false;

                  @Override
                  public boolean hasNext() {
                    if (finished) {
                      return false;
                    }

                    if (nextItem != null) {
                      return true;
                    }

                    try {
                      JsonToken nextToken = parser.nextToken();
                      if (nextToken == JsonToken.END_ARRAY || nextToken == null) {
                        parser.close();
                        finished = true;
                        return false;
                      }

                      nextItem = objectMapper.readTree(parser);

                      // Trigger explicit GC every 1000 items
                      int count = itemCounter.incrementAndGet();
                      if (count % GC_THRESHOLD == 0) {
                        System.gc();
                      }

                      return true;
                    } catch (Exception e) {
                      finished = true;
                      try {
                        parser.close();
                      } catch (Exception closeEx) {
                        // Ignore
                      }
                      throw new StreamingException(
                          "Failed to parse array element at index: " + itemCounter.get(), e);
                    }
                  }

                  @Override
                  public JsonNode next() {
                    if (!hasNext()) {
                      throw new java.util.NoSuchElementException();
                    }
                    JsonNode result = nextItem;
                    nextItem = null;
                    return result;
                  }
                };

        return Multi.createFrom().iterable(iterable);
      }
      // Handle single JSON object
      else if (token == JsonToken.START_OBJECT) {
        JsonNode node = objectMapper.readTree(parser);
        parser.close();
        return Multi.createFrom().item(node);
      }
      // Handle other cases (null, primitives, etc.)
      else {
        parser.close();
        return Multi.createFrom()
            .failure(
                new StreamingException(
                    "Unexpected JSON token: " + token + ". Expected array or object"));
      }

    } catch (Exception e) {
      return Multi.createFrom().failure(new StreamingException("JSON stream parsing failed", e));
    }
  }
}
