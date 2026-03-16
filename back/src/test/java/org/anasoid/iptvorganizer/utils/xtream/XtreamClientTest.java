package org.anasoid.iptvorganizer.utils.xtream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.http.HttpStreamingResponse;
import org.anasoid.iptvorganizer.services.ProxyConfigService;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for XtreamClient using Mockito. */
@ExtendWith(MockitoExtension.class)
class XtreamClientTest {

  @Mock private HttpStreamingService httpStreamingService;
  @Mock private ProxyConfigService proxyConfigService;

  @InjectMocks private XtreamClient xtreamClient;

  private Source testSource;

  @BeforeEach
  void setUp() {
    testSource = new Source();
    testSource.setId(1L);
    testSource.setUrl("http://upstream.example.com");
    testSource.setUsername("upstream_user");
    testSource.setPassword("upstream_pass");
    testSource.setName("Test Source");
  }

  @Test
  void testGetLiveCategories_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get live categories
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getLiveCategories(testSource);

    // Then: Verify correct URL constructed
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl)
        .contains("http://upstream.example.com/player_api.php")
        .contains("action=get_live_categories")
        .contains("username=upstream_user")
        .contains("password=upstream_pass");
  }

  @Test
  void testGetVodCategories_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get VOD categories
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getVodCategories(testSource);

    // Then: Verify correct URL with VOD action
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl).contains("action=get_vod_categories");
  }

  @Test
  void testGetSeriesCategories_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get series categories
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getSeriesCategories(testSource);

    // Then: Verify correct URL with series action
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl).contains("action=get_series_categories");
  }

  @Test
  void testGetLiveStreams_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get live streams
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getLiveStreams(testSource);

    // Then: Verify correct URL with stream action
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl).contains("action=get_live_streams");
  }

  @Test
  void testGetVodStreams_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get VOD streams
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getVodStreams(testSource);

    // Then: Verify correct URL with VOD stream action
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl).contains("action=get_vod_streams");
  }

  @Test
  void testGetSeries_CorrectUrl() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get series
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getSeries(testSource);

    // Then: Verify correct URL with series action
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl).contains("action=get_series");
  }

  @Test
  void testUrlConstruction_RemoveTrailingSlash() {
    // Given: Source URL with trailing slash
    testSource.setUrl("http://upstream.example.com/");

    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get categories
    xtreamClient.getLiveCategories(testSource);

    // Then: Verify URL doesn't have double slashes
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService).streamJsonArray(urlCaptor.capture(), any(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl)
        .contains("http://upstream.example.com/player_api.php")
        .doesNotContain("//player_api.php");
  }

  @Test
  void testHttpOptions_DefaultTimeout() {
    // Given: Mock streaming service
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get categories
    xtreamClient.getLiveCategories(testSource);

    // Then: Verify default timeout is set
    ArgumentCaptor<HttpOptions> optionsCaptor = ArgumentCaptor.forClass(HttpOptions.class);
    verify(httpStreamingService)
        .streamJsonArray(anyString(), optionsCaptor.capture(), eq(testSource));

    HttpOptions capturedOptions = optionsCaptor.getValue();
    assertThat(capturedOptions).isNotNull();
    assertThat(capturedOptions.getTimeout()).isEqualTo(30000L);
    assertThat(capturedOptions.getMaxRetries()).isEqualTo(1);
  }

  @Test
  void testLiveCategories_ReturnsStreamResult() {
    // Given: Mock streaming service with test data
    JsonStreamResult<Map<?, ?>> mockResult =
        new JsonStreamResult<>(java.util.Collections.emptyIterator(), new AtomicLong(0), () -> {});
    when(httpStreamingService.streamJsonArray(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(mockResult);

    // When: Get live categories
    JsonStreamResult<Map<?, ?>> result = xtreamClient.getLiveCategories(testSource);

    // Then: Should return the stream result
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(mockResult);
  }

  @Test
  void testGetSeriesInfoRaw_CorrectUrl() {
    // Given: Mock streaming service
    HttpStreamingResponse mockResponse =
        HttpStreamingResponse.builder()
            .statusCode(200)
            .body(new ByteArrayInputStream("{}".getBytes()))
            .build();
    when(httpStreamingService.streamHttpWithHeaders(
            anyString(), any(HttpOptions.class), isNull(), eq(testSource)))
        .thenReturn(mockResponse);

    // When: Get series info
    HttpStreamingResponse result = xtreamClient.getSeriesInfoRaw(null, testSource, 123);

    // Then: Verify correct URL constructed
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpStreamingService)
        .streamHttpWithHeaders(urlCaptor.capture(), any(), isNull(), eq(testSource));

    String capturedUrl = urlCaptor.getValue();
    assertThat(capturedUrl)
        .contains("http://upstream.example.com/player_api.php")
        .contains("action=get_series_info")
        .contains("series_id=123")
        .contains("username=upstream_user")
        .contains("password=upstream_pass");
  }
}
