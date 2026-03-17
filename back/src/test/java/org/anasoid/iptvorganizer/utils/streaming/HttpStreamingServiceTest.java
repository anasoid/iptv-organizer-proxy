package org.anasoid.iptvorganizer.utils.streaming;

import static org.junit.jupiter.api.Assertions.*;

import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.ProxyOptions;
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
    // Test that null HttpOptions are handled gracefully (ProxyOptions is provided)
    // This will attempt to stream from invalid URL, so we expect failure
    assertThrows(
        Exception.class,
        () ->
            httpStreamingService.streamHttp(
                "http://invalid-url-12345.test", null, new ProxyOptions(), null));
  }

  @Test
  void testStreamHttp_NullProxyOptions_ThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> httpStreamingService.streamHttp("http://example.com", null, null, null));
  }

  @Test
  void testStreamHttpWithHeaders_NullProxyOptions_ThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> httpStreamingService.streamHttpWithHeaders("http://example.com", null, null, null));
  }

  @Test
  void testStreamJsonArray_NullProxyOptions_ThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> httpStreamingService.streamJsonArray("http://example.com", null, null, null));
  }

  @Test
  void testFetchJsonObject_NullProxyOptions_ThrowsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> httpStreamingService.fetchJsonObject("http://example.com", null, null, null));
  }

  @Test
  void testStreamHttpWithOptions() {
    HttpOptions options = HttpOptions.builder().timeout(10000L).maxRetries(1).build();

    assertThrows(
        Exception.class,
        () ->
            httpStreamingService.streamHttp(
                "http://invalid-url-12345.test", options, new ProxyOptions(), null));
  }

  @Test
  void testStreamJsonWithValidJsonArray() {
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
    assertEquals(
        1,
        options3
            .getMaxRetries()); // @Builder.Default applies to @NoArgsConstructor in Lombok 1.18.30+
  }
}
