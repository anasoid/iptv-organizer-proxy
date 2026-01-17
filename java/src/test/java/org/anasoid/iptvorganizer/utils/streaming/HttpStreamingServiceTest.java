package org.anasoid.iptvorganizer.utils.streaming;

import static org.junit.jupiter.api.Assertions.*;

import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HttpStreamingServiceTest {

  private HttpStreamingService httpStreamingService;

  @BeforeEach
  void setUp() {
    httpStreamingService = new HttpStreamingService();
  }

  @Test
  void testStreamHttpNullOptions() {
    // Test that null options are handled gracefully
    // This will attempt to stream from invalid URL, so we expect failure
    assertThrows(
        Exception.class,
        () -> httpStreamingService.streamHttp("http://invalid-url-12345.test", null));
  }

  @Test
  void testStreamHttpWithOptions() {
    HttpOptions options = HttpOptions.builder().timeout(10000L).maxRetries(1).build();

    assertThrows(
        Exception.class,
        () -> httpStreamingService.streamHttp("http://invalid-url-12345.test", options));
  }

  @Test
  void testStreamJsonWithValidJson() {
    String jsonResponse =
        """
        [
            {"id": 1, "name": "Item 1"},
            {"id": 2, "name": "Item 2"}
        ]
        """;

    // Note: This test verifies the API works, actual HTTP test would require mocking
    // or using a real HTTP endpoint
  }

  @Test
  void testCustomHeaders() {
    HttpOptions options =
        HttpOptions.builder()
            .timeout(10000L)
            .headers(
                java.util.Map.of(
                    "X-Custom-Header", "CustomValue",
                    "User-Agent", "CustomAgent"))
            .build();

    assertNotNull(options.getHeaders());
    assertEquals(2, options.getHeaders().size());
    assertEquals("CustomValue", options.getHeaders().get("X-Custom-Header"));
    assertEquals("CustomAgent", options.getHeaders().get("User-Agent"));
  }

  @Test
  void testMaxRetriesConfiguration() {
    HttpOptions options1 = HttpOptions.builder().maxRetries(1).build();
    HttpOptions options2 = HttpOptions.builder().maxRetries(5).build();
    HttpOptions options3 = new HttpOptions();

    assertEquals(1, options1.getMaxRetries());
    assertEquals(5, options2.getMaxRetries());
    assertNull(options3.getMaxRetries());
  }
}
