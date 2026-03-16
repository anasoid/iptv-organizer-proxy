package org.anasoid.iptvorganizer.services.xtream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.dto.RequestType;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for XtreamUserService using Mockito. */
@ExtendWith(MockitoExtension.class)
class XtreamUserServiceTest {

  @Mock private ClientRepository clientRepository;
  @Mock private SourceRepository sourceRepository;
  @Mock private HttpStreamingService httpStreamingService;
  @Mock private CategoryService categoryService;
  @Mock private LiveStreamService liveStreamService;
  @Mock private VodStreamService vodStreamService;
  @Mock private SeriesService seriesService;
  @Mock private ContentFilterService contentFilterService;

  @InjectMocks private XtreamUserService xtreamUserService;

  private Client testClient;
  private Source testSource;

  @BeforeEach
  void setUp() {
    testClient = new Client();
    testClient.setId(1L);
    testClient.setUsername("testclient");
    testClient.setPassword("clientpass");
    testClient.setSourceId(1L);

    testSource = new Source();
    testSource.setId(1L);
    testSource.setUrl("http://upstream.example.com");
    testSource.setUsername("upstream_user");
    testSource.setPassword("upstream_pass");
  }

  @Test
  void testAuthenticate_Success_ReplacesServerInfo() {
    // Given: Mock client and source
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(testSource);

    // Mock upstream authentication response
    Map<String, Object> upstreamAuth = createUpstreamAuthResponse();
    when(httpStreamingService.fetchJsonObject(
            contains("upstream_user"), any(HttpOptions.class), eq(testSource)))
        .thenReturn(upstreamAuth);

    // When: Authenticate via proxy
    Map<String, Object> result =
        xtreamUserService.authenticate(
            "testclient",
            "clientpass",
            new HttpRequestDto("http://proxy.local:9000", RequestType.API, null));

    // Then: Verify server_info and user_info replaced
    assertThat(result).isNotNull();
    assertThat(result).containsKeys("user_info", "server_info");

    @SuppressWarnings("unchecked")
    Map<String, Object> serverInfo = (Map<String, Object>) result.get("server_info");
    assertThat(serverInfo)
        .containsEntry("url", "proxy.local")
        .containsEntry("port", "9000")
        .containsEntry("server_protocol", "http");

    @SuppressWarnings("unchecked")
    Map<String, Object> userInfo = (Map<String, Object>) result.get("user_info");
    assertThat(userInfo)
        .containsEntry("username", "testclient")
        .containsEntry("password", "clientpass");
  }

  @Test
  void testAuthenticate_Success_WithHttpsProxyUrl() {
    // Given: HTTPS proxy URL
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(testSource);

    Map<String, Object> upstreamAuth = createUpstreamAuthResponse();
    when(httpStreamingService.fetchJsonObject(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(upstreamAuth);

    // When: Authenticate with HTTPS proxy
    Map<String, Object> result =
        xtreamUserService.authenticate(
            "testclient",
            "clientpass",
            new HttpRequestDto("https://proxy.local:8443", RequestType.API, null));

    // Then: Verify HTTPS port mapping
    @SuppressWarnings("unchecked")
    Map<String, Object> serverInfo = (Map<String, Object>) result.get("server_info");
    assertThat(serverInfo)
        .containsEntry("url", "proxy.local")
        .containsEntry("https_port", "8443")
        .containsEntry("server_protocol", "https");
  }

  @Test
  void testAuthenticate_SourceNotFound() {
    // Given: Source not found
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(null);

    // When/Then: Should throw exception
    assertThatThrownBy(
            () ->
                xtreamUserService.authenticate(
                    "testclient",
                    "clientpass",
                    new HttpRequestDto("http://proxy.local:9000", RequestType.API, null)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Source not found for client");
  }

  @Test
  void testAuthenticateAndValidateClient_Success() {
    // Given: Valid credentials
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(testSource);

    // When: Authenticate and validate
    ClientAuthenticationResult result =
        xtreamUserService.authenticateAndValidateClient("testclient", "clientpass");

    // Then: Should return client and source
    assertThat(result).isNotNull();
    assertThat(result.getClient()).isEqualTo(testClient);
    assertThat(result.getSource()).isEqualTo(testSource);
  }

  @Test
  void testAuthenticateAndValidateClient_EmptyUsername() {
    // When/Then: Empty username should throw UnauthorizedException
    assertThatThrownBy(() -> xtreamUserService.authenticateAndValidateClient("", "password"))
        .isInstanceOf(org.anasoid.iptvorganizer.exceptions.UnauthorizedException.class);
  }

  @Test
  void testAuthenticateAndValidateClient_NullPassword() {
    // When/Then: Null password should throw UnauthorizedException
    assertThatThrownBy(() -> xtreamUserService.authenticateAndValidateClient("username", null))
        .isInstanceOf(org.anasoid.iptvorganizer.exceptions.UnauthorizedException.class);
  }

  @Test
  void testAuthenticateAndValidateClient_ClientNotFound() {
    // Given: Client not found
    when(clientRepository.findByUsernameAndPassword("invalid", "password"))
        .thenThrow(new RuntimeException("Client not found"));

    // When/Then: Should throw exception
    assertThatThrownBy(() -> xtreamUserService.authenticateAndValidateClient("invalid", "password"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void testAuthenticateAndValidateClient_SourceNotConfigured() {
    // Given: Source not found for client
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(null);

    // When/Then: Should throw ForbiddenException
    assertThatThrownBy(
            () -> xtreamUserService.authenticateAndValidateClient("testclient", "clientpass"))
        .isInstanceOf(org.anasoid.iptvorganizer.exceptions.ForbiddenException.class)
        .hasMessage("Source not configured for this client");
  }

  @Test
  void testAuthenticate_InvalidAuthResponse() {
    // Given: Invalid upstream response (missing required fields)
    when(clientRepository.findByUsernameAndPassword("testclient", "clientpass"))
        .thenReturn(testClient);
    when(sourceRepository.findById(1L)).thenReturn(testSource);

    Map<String, Object> invalidResponse = new HashMap<>();
    invalidResponse.put("invalid_field", "value");
    when(httpStreamingService.fetchJsonObject(anyString(), any(HttpOptions.class), eq(testSource)))
        .thenReturn(invalidResponse);

    // When/Then: Should throw exception
    assertThatThrownBy(
            () ->
                xtreamUserService.authenticate(
                    "testclient",
                    "clientpass",
                    new HttpRequestDto("http://proxy.local:9000", RequestType.API, null)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Invalid authentication response from upstream");
  }

  /**
   * Create a mock upstream authentication response
   *
   * @return Map with user_info and server_info
   */
  private Map<String, Object> createUpstreamAuthResponse() {
    Map<String, Object> response = new HashMap<>();

    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put("username", "upstream_user");
    userInfo.put("password", "upstream_pass");
    userInfo.put("auth", 1);
    userInfo.put("status", "Active");
    response.put("user_info", userInfo);

    Map<String, Object> serverInfo = new HashMap<>();
    serverInfo.put("url", "upstream.example.com");
    serverInfo.put("port", "8080");
    serverInfo.put("https_port", "8443");
    serverInfo.put("server_protocol", "http");
    response.put("server_info", serverInfo);

    return response;
  }
}
