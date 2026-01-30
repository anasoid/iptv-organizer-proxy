package org.anasoid.iptvorganizer.testutils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/** Utility helper for WireMock stubbing of Xtream API endpoints in integration tests. */
public class WireMockXtreamHelper {

  /**
   * Stub successful authentication response from Xtream API.
   *
   * @param wireMock WireMock server instance
   * @param username Source username
   * @param password Source password
   */
  public static void stubAuthenticationSuccess(
      WireMockExtension wireMock, String username, String password) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .withQueryParam("username", equalTo(username))
            .withQueryParam("password", equalTo(password))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(createAuthResponse(username, password))));
  }

  /**
   * Stub authentication failure (401 Unauthorized).
   *
   * @param wireMock WireMock server instance
   */
  public static void stubAuthenticationFailure(WireMockExtension wireMock) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .willReturn(
                aResponse().withStatus(401).withBody("{\"error\": \"Invalid credentials\"}")));
  }

  /**
   * Stub live categories response.
   *
   * @param wireMock WireMock server instance
   * @param count Number of categories to return
   */
  public static void stubLiveCategories(WireMockExtension wireMock, int count) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .withQueryParam("action", equalTo("get_live_categories"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(createCategoriesJson(count))));
  }

  /**
   * Stub VOD categories response.
   *
   * @param wireMock WireMock server instance
   * @param count Number of categories to return
   */
  public static void stubVodCategories(WireMockExtension wireMock, int count) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .withQueryParam("action", equalTo("get_vod_categories"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(createCategoriesJson(count))));
  }

  /**
   * Stub series categories response.
   *
   * @param wireMock WireMock server instance
   * @param count Number of categories to return
   */
  public static void stubSeriesCategories(WireMockExtension wireMock, int count) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .withQueryParam("action", equalTo("get_series_categories"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(createCategoriesJson(count))));
  }

  /**
   * Stub live streams response.
   *
   * @param wireMock WireMock server instance
   * @param count Number of streams to return
   */
  public static void stubLiveStreams(WireMockExtension wireMock, int count) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .withQueryParam("action", equalTo("get_live_streams"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(createStreamsJson(count))));
  }

  /**
   * Stub timeout response (slow server).
   *
   * @param wireMock WireMock server instance
   * @param delayMs Delay in milliseconds
   */
  public static void stubTimeout(WireMockExtension wireMock, int delayMs) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(delayMs)));
  }

  /**
   * Stub 500 error response.
   *
   * @param wireMock WireMock server instance
   */
  public static void stub500Error(WireMockExtension wireMock) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .willReturn(
                aResponse().withStatus(500).withBody("{\"error\": \"Internal Server Error\"}")));
  }

  /**
   * Stub 503 error response (service unavailable).
   *
   * @param wireMock WireMock server instance
   */
  public static void stub503Error(WireMockExtension wireMock) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .willReturn(
                aResponse().withStatus(503).withBody("{\"error\": \"Service Unavailable\"}")));
  }

  /**
   * Stub malformed JSON response.
   *
   * @param wireMock WireMock server instance
   */
  public static void stubMalformedJson(WireMockExtension wireMock) {
    wireMock.stubFor(
        get(urlPathEqualTo("/player_api.php"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ invalid json")));
  }

  /**
   * Create authentication response JSON.
   *
   * @param username The username
   * @param password The password
   * @return JSON string
   */
  private static String createAuthResponse(String username, String password) {
    return String.format(
        """
        {
          "user_info": {
            "username": "%s",
            "password": "%s",
            "auth": 1,
            "status": "Active",
            "exp_date": "unlimited",
            "created_at": 1234567890
          },
          "server_info": {
            "url": "upstream.example.com",
            "port": "8080",
            "https_port": "8443",
            "server_protocol": "http"
          }
        }
        """,
        username, password);
  }

  /**
   * Create JSON array of categories.
   *
   * @param count Number of categories
   * @return JSON string
   */
  private static String createCategoriesJson(int count) {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 1; i <= count; i++) {
      sb.append(
          String.format(
              """
              {
                "category_id": %d,
                "category_name": "Category %d",
                "parent_id": 0
              }%s
              """,
              i, i, i < count ? "," : ""));
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Create JSON array of streams.
   *
   * @param count Number of streams
   * @return JSON string
   */
  private static String createStreamsJson(int count) {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 1; i <= count; i++) {
      sb.append(
          String.format(
              """
              {
                "num": %d,
                "name": "Stream %d",
                "stream_id": %d,
                "stream_icon": "",
                "category_id": 1,
                "added": "2024-01-01",
                "is_adult": "0",
                "category_ids": [1]
              }%s
              """,
              i, i, i, i < count ? "," : ""));
    }
    sb.append("]");
    return sb.toString();
  }
}
