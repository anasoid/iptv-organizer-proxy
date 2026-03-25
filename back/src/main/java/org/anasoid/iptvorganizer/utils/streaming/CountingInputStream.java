package org.anasoid.iptvorganizer.utils.streaming;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InputStream wrapper that counts bytes read.
 *
 * <p>Tracks all bytes read from the underlying stream for monitoring and logging purposes.
 * Thread-safe via AtomicLong.
 */
public class CountingInputStream extends FilterInputStream {
  private final AtomicLong bytesRead = new AtomicLong(0);

  /**
   * Wrap an input stream with byte counting.
   *
   * @param in The underlying input stream
   */
  public CountingInputStream(InputStream in) {
    super(in);
  }

  @Override
  public int read() throws IOException {
    int result = super.read();
    if (result != -1) {
      bytesRead.incrementAndGet();
    }
    return result;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int result = super.read(b, off, len);
    if (result != -1) {
      bytesRead.addAndGet(result);
    }
    return result;
  }

  /**
   * Get the total bytes read so far.
   *
   * @return Number of bytes read
   */
  public long getBytesRead() {
    return bytesRead.get();
  }

  /**
   * Get the atomic counter for bytes read.
   *
   * @return AtomicLong that tracks bytes read
   */
  public AtomicLong getBytesReadAtomic() {
    return bytesRead;
  }
}
