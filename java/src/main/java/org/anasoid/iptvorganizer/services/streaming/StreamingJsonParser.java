package org.anasoid.iptvorganizer.services.streaming;

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
import org.anasoid.iptvorganizer.exceptions.StreamingException;

@ApplicationScoped
public class StreamingJsonParser {

  private static final int CHUNK_SIZE = 128 * 1024; // 128KB
  private static final int GC_THRESHOLD = 1000;

  @Inject ObjectMapper objectMapper;

  /**
   * Parse JSON array from input stream and emit items as Multi<T> Processes in 128KB chunks with
   * explicit GC every 1000 items
   */
  public <T> Multi<T> parseJsonArray(InputStream inputStream, Class<T> targetClass) {
    return Multi.createFrom()
        .emitter(
            emitter -> {
              try {
                JsonFactory factory = objectMapper.getFactory();
                JsonParser parser = factory.createParser(inputStream);

                // Verify we have a JSON array
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                  emitter.fail(new StreamingException("Expected JSON array at start of stream"));
                  return;
                }

                AtomicInteger itemCounter = new AtomicInteger(0);

                // Parse each array element
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                  try {
                    T item = objectMapper.readValue(parser, targetClass);
                    emitter.emit(item);

                    // Trigger explicit GC every 1000 items
                    int count = itemCounter.incrementAndGet();
                    if (count % GC_THRESHOLD == 0) {
                      System.gc();
                    }
                  } catch (Exception e) {
                    emitter.fail(
                        new StreamingException(
                            "Failed to parse array element at index: " + itemCounter.get(), e));
                    return;
                  }
                }

                parser.close();
                emitter.complete();

              } catch (Exception e) {
                emitter.fail(new StreamingException("JSON parsing failed", e));
              }
            });
  }

  /**
   * Parse JSON stream (array or single object) and emit as JsonNode More flexible than
   * parseJsonArray - handles various JSON structures
   */
  public Multi<JsonNode> parseJsonStream(InputStream inputStream) {
    return Multi.createFrom()
        .emitter(
            emitter -> {
              try {
                JsonFactory factory = objectMapper.getFactory();
                JsonParser parser = factory.createParser(inputStream);

                JsonToken token = parser.nextToken();

                // Handle JSON array
                if (token == JsonToken.START_ARRAY) {
                  AtomicInteger itemCounter = new AtomicInteger(0);
                  while (parser.nextToken() != JsonToken.END_ARRAY) {
                    try {
                      JsonNode node = objectMapper.readTree(parser);
                      emitter.emit(node);

                      // Trigger explicit GC every 1000 items
                      int count = itemCounter.incrementAndGet();
                      if (count % GC_THRESHOLD == 0) {
                        System.gc();
                      }
                    } catch (Exception e) {
                      emitter.fail(
                          new StreamingException(
                              "Failed to parse array element at index: " + itemCounter.get(), e));
                      return;
                    }
                  }
                  emitter.complete();
                }
                // Handle single JSON object
                else if (token == JsonToken.START_OBJECT) {
                  JsonNode node = objectMapper.readTree(parser);
                  emitter.emit(node);
                  emitter.complete();
                }
                // Handle other cases (null, primitives, etc.)
                else {
                  emitter.fail(
                      new StreamingException(
                          "Unexpected JSON token: " + token + ". Expected array or object"));
                }

                parser.close();

              } catch (Exception e) {
                emitter.fail(new StreamingException("JSON stream parsing failed", e));
              }
            });
  }
}
