package org.anasoid.iptvorganizer.controllers.xtream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.xtream.ClientAuthenticationResult;
import org.anasoid.iptvorganizer.services.xtream.ContentFilterService;
import org.anasoid.iptvorganizer.services.xtream.FilterContext;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.StreamModeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for TimeshiftController */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeshiftControllerTest {

  @Mock private XtreamUserService xtreamUserService;
  @Mock private ClientService clientService;
  @Mock private ContentFilterService contentFilterService;
  @Mock private CategoryService categoryService;
  @Mock private LiveStreamService liveStreamService;
  @Mock private StreamModeHandler streamModeHandler;
  @InjectMocks private TimeshiftController timeshiftController;

  private Client testClient;
  private Source testSource;
  private LiveStream testStream;
  private UriInfo uriInfo;

  @BeforeEach
  void setUp() {
    testClient =
        Client.builder().id(1L).username("testuser").password("testpass").sourceId(1L).build();

    testSource =
        Source.builder()
            .id(1L)
            .username("upstream_user")
            .password("upstream_pass")
            .url("http://upstream.provider.com")
            .build();

    testStream =
        LiveStream.builder()
            .id(1L)
            .externalId(100)
            .sourceId(1L)
            .name("Test Stream")
            .categoryId(1)
            .build();

    uriInfo = mock(UriInfo.class);
    URI baseUri = URI.create("http://localhost:8080/");
    when(uriInfo.getBaseUri()).thenReturn(baseUri);
  }

  @Test
  void testHandleTimeshiftStream_invalidStreamId() {
    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "50", "1707840000", "invalid_id", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_streamNotFound() {
    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(null);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "100", "1707840000", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_invalidStartTimestamp() {
    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "100", "invalid_start", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_withinArchiveWindow() {
    long now = System.currentTimeMillis() / 1000;
    // Request from 5 days ago, within 7 days archive available

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(true);
    when(clientService.resolveConnectXtreamStream(testClient, testSource))
        .thenReturn(ConnectXtreamStreamMode.REDIRECT);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "2026-03-15:03-30", "50", "3600", "ts", uriInfo, null);

    // Should succeed since start time is within archive window
    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_accessDenied() {

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(false);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "2026-03-15:03-30", "50", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_validRequest_redirectMode() {

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(true);
    when(clientService.resolveConnectXtreamStream(testClient, testSource))
        .thenReturn(ConnectXtreamStreamMode.REDIRECT);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "100", "50", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_validRequest_proxyMode() {

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(true);
    when(clientService.resolveConnectXtreamStream(testClient, testSource))
        .thenReturn(ConnectXtreamStreamMode.PROXY);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "2026-03-15:03-30", "50", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
  }

  @Test
  void testHandleTimeshiftStream_urlFormat_correctlyFormatted() {

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, testSource));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(true);
    when(clientService.resolveConnectXtreamStream(testClient, testSource))
        .thenReturn(ConnectXtreamStreamMode.REDIRECT);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "2026-03-15:03-30", "50", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());

    // Verify the redirect URL uses official Xtream API format
    String redirectUrl = response.getLocation().toString();
    assertThat(redirectUrl)
        .isEqualTo(
            "http://upstream.provider.com/timeshift/upstream_user/upstream_pass/50/2026-03-15:03-30/3600.ts");
  }

  @Test
  void testHandleTimeshiftStream_credentialsUrlEncoded() {

    // Create source with special characters in credentials
    Source sourceWithSpecialChars =
        Source.builder()
            .id(1L)
            .username("user@domain.com")
            .password("pass&123=test")
            .url("http://upstream.provider.com")
            .build();

    when(xtreamUserService.authenticateAndValidateClient("testuser", "testpass"))
        .thenReturn(new ClientAuthenticationResult(testClient, sourceWithSpecialChars));
    when(liveStreamService.findBySourceAndStreamId(1L, 3600)).thenReturn(testStream);
    when(categoryService.findBySourceAndCategoryId(1L, 1, "live")).thenReturn(null);
    when(contentFilterService.buildFilterContext(testClient)).thenReturn(new FilterContext());
    when(contentFilterService.shouldIncludeStream(any(), any(), any())).thenReturn(true);
    when(clientService.resolveConnectXtreamStream(testClient, sourceWithSpecialChars))
        .thenReturn(ConnectXtreamStreamMode.REDIRECT);

    Response response =
        timeshiftController.handleTimeshiftStream(
            "testuser", "testpass", "2026-03-15:03-30", "50", "3600", "ts", uriInfo, null);

    assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
    String redirectUrl = response.getLocation().toString();

    // Verify credentials are URL encoded
    assertThat(redirectUrl)
        .contains("/user%40domain.com/") // @ encoded as %40
        .contains("/pass%26123%3Dtest/"); // & as %26, = as %3D
  }
}
