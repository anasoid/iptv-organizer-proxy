package org.anasoid.iptvorganizer.utils.streaming;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Result wrapper for streaming JSON parsing operations.
 *
 * <p>Provides lazy evaluation via Iterator (items only parsed when requested) and metadata tracking
 * (bytes read, parse statistics). Implements Closeable for resource management.
 *
 * @param <T> The type of items being streamed
 */
public class JsonStreamResult<T> implements Closeable {
  private final Iterator<T> iterator;
  private final AtomicLong bytesRead;
  private final Closeable resource; // For closing InputStream

  /**
   * Create a new JsonStreamResult.
   *
   * @param iterator The lazy iterator for streaming items
   * @param bytesRead Atomic counter tracking bytes read from the stream
   * @param resource The underlying resource (InputStream/Parser) to close
   */
  public JsonStreamResult(Iterator<T> iterator, AtomicLong bytesRead, Closeable resource) {
    this.iterator = iterator;
    this.bytesRead = bytesRead;
    this.resource = resource;
  }

  /**
   * Get the iterator for streaming items.
   *
   * @return Iterator that provides lazy evaluation of parsed items
   */
  public Iterator<T> iterator() {
    return iterator;
  }

  /**
   * Get the total bytes read from the stream so far.
   *
   * @return Number of bytes read
   */
  public long getBytesRead() {
    return bytesRead.get();
  }

  /**
   * Close the underlying resource (InputStream and JsonParser).
   *
   * @throws IOException if closing the resource fails
   */
  @Override
  public void close() throws IOException {
    if (resource != null) {
      resource.close();
    }
  }
}
