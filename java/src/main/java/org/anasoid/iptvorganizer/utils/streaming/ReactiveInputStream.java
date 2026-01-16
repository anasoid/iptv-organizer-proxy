package org.anasoid.iptvorganizer.utils.streaming;

import io.smallrye.mutiny.Multi;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Memory-efficient InputStream that pulls data from a reactive Multi&lt;Buffer&gt; stream on-demand
 * with TRUE backpressure.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li><b>True backpressure:</b> HTTP fetching pauses when JSON parsing is slow
 *   <li><b>Constant memory:</b> Max 2 buffers in queue (16-64KB) + current buffer being read
 *   <li><b>No event loop blocking:</b> Designed to run on worker threads via .emitOn()
 *   <li><b>Proper lifecycle:</b> Handles cancellation, errors, and completion
 * </ul>
 *
 * <p><b>Memory guarantee:</b> O(1) memory usage - independent of total stream size
 *
 * <p><b>Critical:</b> This implementation uses manual subscription.request(1) to provide true
 * backpressure. HTTP buffers are only fetched when the current buffer is fully consumed.
 *
 * <p><b>Queue capacity:</b> Set to 2 to allow async producer/consumer without deadlock on
 * synchronous sources (e.g., tests). Still provides strong backpressure as queue fills quickly.
 *
 * @see HttpStreamingService#streamJson
 */
public class ReactiveInputStream extends InputStream {

  private static final Logger LOGGER = Logger.getLogger(ReactiveInputStream.class.getName());

  // Timeout for waiting on next buffer
  private static final long BUFFER_TIMEOUT_SECONDS = 60;

  // Queue with capacity of 2 - allows for async producer/consumer
  // Small enough for memory efficiency, large enough to avoid deadlock with sync sources
  private final BlockingQueue<BufferOrSignal> queue = new LinkedBlockingQueue<>(2);

  // Flow subscription for manual demand control
  private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

  // Current buffer being read
  private Buffer currentBuffer;

  // Current read position in buffer
  private int currentPosition;

  // Stream closed flag
  private final AtomicBoolean closed = new AtomicBoolean(false);

  // Subscription ready flag
  private final AtomicBoolean subscriptionReady = new AtomicBoolean(false);

  // Completion/error signals
  private static final class BufferOrSignal {
    final Buffer buffer;
    final Throwable error;
    final boolean complete;

    static BufferOrSignal ofBuffer(Buffer buffer) {
      return new BufferOrSignal(buffer, null, false);
    }

    static BufferOrSignal ofError(Throwable error) {
      return new BufferOrSignal(null, error, false);
    }

    static BufferOrSignal ofComplete() {
      return new BufferOrSignal(null, null, true);
    }

    private BufferOrSignal(Buffer buffer, Throwable error, boolean complete) {
      this.buffer = buffer;
      this.error = error;
      this.complete = complete;
    }
  }

  /**
   * Creates a reactive input stream that pulls buffers from the given Multi on-demand.
   *
   * <p>Subscribes immediately using Flow.Subscriber with manual request(1) pattern.
   *
   * @param source The reactive stream of buffers (typically from HTTP response)
   */
  public ReactiveInputStream(Multi<Buffer> source) {
    this.currentBuffer = null;
    this.currentPosition = 0;

    // Subscribe with manual subscription control
    // Use runSubscriptionOn to avoid blocking issues with synchronous sources
    source
        .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor())
        .subscribe(
            new Flow.Subscriber<Buffer>() {
              @Override
              public void onSubscribe(Flow.Subscription s) {
                subscription.set(s);
                subscriptionReady.set(true);
                // Request first item immediately
                s.request(1);
                LOGGER.fine("Subscribed to source Multi - requested first item");
              }

              @Override
              public void onNext(Buffer item) {
                try {
                  LOGGER.fine(() -> "Received buffer of " + item.length() + " bytes");
                  // This will BLOCK if queue is full (capacity=1)
                  // This is the backpressure mechanism - producer waits for consumer
                  boolean offered =
                      queue.offer(
                          BufferOrSignal.ofBuffer(item), BUFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  if (!offered) {
                    LOGGER.severe("Timeout while queueing buffer - cancelling subscription");
                    Flow.Subscription sub = subscription.get();
                    if (sub != null) {
                      sub.cancel();
                    }
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  LOGGER.severe("Interrupted while queueing buffer");
                }
              }

              @Override
              public void onError(Throwable failure) {
                try {
                  LOGGER.severe(() -> "Stream error: " + failure.getMessage());
                  queue.offer(
                      BufferOrSignal.ofError(failure), BUFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }

              @Override
              public void onComplete() {
                try {
                  LOGGER.info("Stream completed");
                  queue.offer(
                      BufferOrSignal.ofComplete(), BUFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            });

    LOGGER.fine("ReactiveInputStream created with queue-based backpressure (capacity=2)");
  }

  /**
   * Fetch next buffer from the reactive stream. Blocks until buffer is available.
   *
   * <p>This method implements the backpressure mechanism: it only requests the next buffer from the
   * HTTP stream AFTER the current buffer has been fully consumed.
   *
   * @return true if next buffer was fetched, false if stream completed
   * @throws IOException if stream error occurred or timeout
   */
  private boolean fetchNextBuffer() throws IOException {
    // Release current buffer immediately for GC
    currentBuffer = null;
    currentPosition = 0;

    // Wait for subscription to be ready
    while (!subscriptionReady.get() && !closed.get()) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for subscription", e);
      }
    }

    if (closed.get()) {
      return false;
    }

    // Poll queue with timeout (this blocks until next buffer arrives)
    BufferOrSignal signal;
    try {
      signal = queue.poll(BUFFER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      if (signal == null) {
        throw new IOException(
            "Timeout waiting for next buffer after " + BUFFER_TIMEOUT_SECONDS + " seconds");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for next buffer", e);
    }

    // Handle error
    if (signal.error != null) {
      throw new IOException("Stream error occurred", signal.error);
    }

    // Handle completion
    if (signal.complete) {
      LOGGER.fine("Stream completed - no more buffers");
      return false; // End of stream
    }

    // Got a buffer
    currentBuffer = signal.buffer;
    LOGGER.fine(() -> "Fetched next buffer: " + currentBuffer.length() + " bytes");

    // CRITICAL: Request next item ONLY after current buffer is consumed
    // This is the backpressure control point
    Flow.Subscription sub = subscription.get();
    if (sub != null) {
      sub.request(1);
      LOGGER.fine("Requested next buffer from subscription");
    }

    return true;
  }

  @Override
  public int read() throws IOException {
    if (closed.get()) {
      throw new IOException("Stream is closed");
    }

    // Check if we need to fetch next buffer
    if (currentBuffer == null || currentPosition >= currentBuffer.length()) {
      boolean hasNext = fetchNextBuffer();
      if (!hasNext) {
        return -1; // End of stream
      }
    }

    // Read single byte from current buffer
    return currentBuffer.getByte(currentPosition++) & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (closed.get()) {
      throw new IOException("Stream is closed");
    }

    if (b == null) {
      throw new NullPointerException("Byte array is null");
    }
    if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException(
          "Invalid offset=" + off + " or length=" + len + " for array of length=" + b.length);
    }
    if (len == 0) {
      return 0;
    }

    int totalRead = 0;

    while (totalRead < len) {
      // Check if we need to fetch next buffer
      if (currentBuffer == null || currentPosition >= currentBuffer.length()) {
        boolean hasNext = fetchNextBuffer();
        if (!hasNext) {
          // End of stream
          return totalRead == 0 ? -1 : totalRead;
        }
      }

      // Calculate how many bytes to copy from current buffer
      int available = currentBuffer.length() - currentPosition;
      int toRead = Math.min(len - totalRead, available);

      // Copy bytes from buffer
      currentBuffer.getBytes(currentPosition, currentPosition + toRead, b, off + totalRead);

      currentPosition += toRead;
      totalRead += toRead;
    }

    return totalRead;
  }

  @Override
  public int available() throws IOException {
    if (closed.get()) {
      throw new IOException("Stream is closed");
    }

    if (currentBuffer != null && currentPosition < currentBuffer.length()) {
      return currentBuffer.length() - currentPosition;
    }
    return 0;
  }

  @Override
  public void close() throws IOException {
    if (closed.getAndSet(true)) {
      return; // Already closed
    }

    LOGGER.fine("Closing ReactiveInputStream");

    // Cancel subscription to stop fetching more data
    Flow.Subscription sub = subscription.get();
    if (sub != null) {
      sub.cancel();
    }

    // Release current buffer
    currentBuffer = null;

    // Clear queue
    queue.clear();
  }

  @Override
  public boolean markSupported() {
    return false;
  }
}
