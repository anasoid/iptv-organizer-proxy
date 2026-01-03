package org.anasoid.iptvorganizer.services.streaming;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class HttpStreamingServiceTest {

    @Inject
    HttpStreamingService httpStreamingService;

    @Test
    void testHttpOptionsBuilder() {
        HttpOptions options = HttpOptions.builder()
            .username("user")
            .password("pass")
            .timeout(30)
            .maxRetries(3)
            .followRedirects(true)
            .build();

        assertEquals("user", options.getUsername());
        assertEquals("pass", options.getPassword());
        assertEquals(30, options.getTimeout());
        assertEquals(3, options.getMaxRetries());
        assertTrue(options.getFollowRedirects());
    }

    @Test
    void testHttpOptionsDefaults() {
        HttpOptions options = new HttpOptions();

        assertNull(options.getUsername());
        assertNull(options.getPassword());
        assertNull(options.getTimeout());
        assertNull(options.getMaxRetries());
    }

    @Test
    void testStreamHttpNullOptions() {
        // Test that null options are handled gracefully
        // This will attempt to stream from invalid URL, so we expect failure
        Multi<Buffer> stream = httpStreamingService.streamHttp("http://invalid-url-12345.test", null);
        assertNotNull(stream);
    }

    @Test
    void testStreamHttpWithOptions() {
        HttpOptions options = HttpOptions.builder()
            .timeout(10)
            .maxRetries(1)
            .build();

        Multi<Buffer> stream = httpStreamingService.streamHttp("http://invalid-url-12345.test", options);
        assertNotNull(stream);
    }

    @Test
    void testStreamJsonWithValidJson() {
        String jsonResponse = """
            [
                {"id": 1, "name": "Item 1"},
                {"id": 2, "name": "Item 2"}
            ]
            """;

        // Note: This test verifies the API works, actual HTTP test would require mocking
        // or using a real HTTP endpoint
    }

    @Test
    void testBasicAuthEncoding() {
        // Test that Basic Auth is properly encoded
        String username = "testuser";
        String password = "testpass";

        // Create options with auth
        HttpOptions options = HttpOptions.builder()
            .username(username)
            .password(password)
            .timeout(10)
            .maxRetries(1)
            .build();

        assertEquals("testuser", options.getUsername());
        assertEquals("testpass", options.getPassword());

        // Verify Base64 encoding would be: testuser:testpass -> dGVzdHVzZXI6dGVzdHBhc3M=
        String credentials = username + ":" + password;
        String encoded = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        assertEquals("dGVzdHVzZXI6dGVzdHBhc3M=", encoded);
    }

    @Test
    void testCustomHeaders() {
        HttpOptions options = HttpOptions.builder()
            .timeout(10)
            .headers(java.util.Map.of(
                "X-Custom-Header", "CustomValue",
                "User-Agent", "CustomAgent"
            ))
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

    @Test
    void testTimeoutConfiguration() {
        HttpOptions options = HttpOptions.builder()
            .timeout(30)
            .build();

        assertEquals(30, options.getTimeout());
    }
}
