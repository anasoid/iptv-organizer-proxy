package org.anasoid.iptvorganizer.utils.streaming;

import static org.junit.jupiter.api.Assertions.*;

import io.smallrye.mutiny.Multi;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ReactiveInputStreamTest {

  @Test
  void testReadSingleByte() throws IOException {
    // Create a Multi with a small buffer
    Buffer buffer = Buffer.buffer("Hello");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Read first byte (lazy subscription happens here)
    int firstByte = inputStream.read();
    assertEquals('H', firstByte);

    // Read second byte
    int secondByte = inputStream.read();
    assertEquals('e', secondByte);

    inputStream.close();
  }

  @Test
  void testReadByteArray() throws IOException {
    // Create a Multi with test data
    Buffer buffer = Buffer.buffer("Hello World");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Read into byte array
    byte[] data = new byte[11];
    int bytesRead = inputStream.read(data, 0, 11);

    assertEquals(11, bytesRead);
    assertEquals("Hello World", new String(data));

    inputStream.close();
  }

  @Test
  void testReadAcrossMultipleBuffers() throws IOException {
    // Create a Multi with multiple buffers
    Multi<Buffer> source =
        Multi.createFrom()
            .items(Buffer.buffer("Hello "), Buffer.buffer("World"), Buffer.buffer("!"));

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Read all data
    byte[] data = new byte[12];
    int bytesRead = inputStream.read(data, 0, 12);

    assertEquals(12, bytesRead);
    assertEquals("Hello World!", new String(data));

    // Verify end of stream
    assertEquals(-1, inputStream.read());

    inputStream.close();
  }

  @Test
  void testStreamCompletion() throws IOException {
    // Create a Multi with limited data
    Multi<Buffer> source = Multi.createFrom().item(Buffer.buffer("Test"));

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Read all data
    byte[] data = new byte[4];
    int bytesRead = inputStream.read(data, 0, 4);
    assertEquals(4, bytesRead);
    assertEquals("Test", new String(data));

    // Next read should return -1 (end of stream)
    int result = inputStream.read();
    assertEquals(-1, result);

    inputStream.close();
  }

  @Test
  void testAvailable() throws IOException {
    Buffer buffer = Buffer.buffer("Hello");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Before reading, available returns 0 (no buffer loaded yet)
    assertEquals(0, inputStream.available());

    // Read one byte to load the buffer
    inputStream.read();

    // Now available should report bytes left in current buffer
    int available = inputStream.available();
    assertEquals(4, available); // "ello" remaining

    inputStream.close();
  }

  @Test
  void testMarkNotSupported() {
    Buffer buffer = Buffer.buffer("Test");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    assertFalse(inputStream.markSupported());

    try {
      inputStream.close();
    } catch (IOException e) {
      fail("Close should not throw exception");
    }
  }

  @Test
  void testEmptyStream() throws IOException {
    // Create an empty Multi
    Multi<Buffer> source = Multi.createFrom().empty();

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Reading from empty stream should return -1
    assertEquals(-1, inputStream.read());

    inputStream.close();
  }

  @Test
  void testPartialRead() throws IOException {
    // Create a Multi with data
    Buffer buffer = Buffer.buffer("Hello World");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Read only part of the data
    byte[] data = new byte[5];
    int bytesRead = inputStream.read(data, 0, 5);

    assertEquals(5, bytesRead);
    assertEquals("Hello", new String(data));

    // Read the rest
    byte[] data2 = new byte[6];
    int bytesRead2 = inputStream.read(data2, 0, 6);

    assertEquals(6, bytesRead2);
    assertEquals(" World", new String(data2));

    inputStream.close();
  }

  @Test
  void testInvalidReadParameters() {
    Buffer buffer = Buffer.buffer("Test");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    byte[] data = new byte[10];

    // Test null array
    assertThrows(NullPointerException.class, () -> inputStream.read(null, 0, 5));

    // Test negative offset
    assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(data, -1, 5));

    // Test negative length
    assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(data, 0, -1));

    // Test offset + length > array length
    assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(data, 5, 10));

    try {
      inputStream.close();
    } catch (IOException e) {
      fail("Close should not throw exception");
    }
  }

  @Test
  void testClose() throws IOException {
    Buffer buffer = Buffer.buffer("Test");
    Multi<Buffer> source = Multi.createFrom().item(buffer);

    ReactiveInputStream inputStream = new ReactiveInputStream(source);

    // Close should not throw
    inputStream.close();

    // After close, reads should throw IOException
    assertThrows(IOException.class, () -> inputStream.read());
  }
}
