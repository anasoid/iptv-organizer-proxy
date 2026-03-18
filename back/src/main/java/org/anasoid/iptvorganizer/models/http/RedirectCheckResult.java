package org.anasoid.iptvorganizer.models.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of upstream redirect check (for credential hiding)
 *
 * <p>Used when disableStreamProxy=true and useRedirect=false to determine if upstream supports
 * credential-free redirects. Makes a GET request with followRedirects=false to detect redirects
 * without exposing source credentials to the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedirectCheckResult {
  /** Whether the upstream responded with a redirect status (301, 302, 307, 308) */
  private boolean isRedirect;

  /** Location header value if redirect was detected */
  private String location;

  /** HTTP status code from upstream response */
  private int statusCode;

  /** Whether an error occurred during redirect check */
  private boolean error;

  /** Error message if error occurred */
  private String errorMessage;

  /**
   * Create a successful redirect result
   *
   * @param location The Location header value from upstream
   * @return RedirectCheckResult indicating redirect was detected
   */
  public static RedirectCheckResult redirect(String location) {
    return RedirectCheckResult.builder().isRedirect(true).location(location).error(false).build();
  }

  /**
   * Create a no-redirect result (upstream returned non-redirect status)
   *
   * @param statusCode The HTTP status code from upstream
   * @return RedirectCheckResult indicating no redirect was detected
   */
  public static RedirectCheckResult noRedirect(int statusCode) {
    return RedirectCheckResult.builder()
        .isRedirect(false)
        .statusCode(statusCode)
        .error(false)
        .build();
  }

  /**
   * Create an error result (exception during redirect check)
   *
   * @param errorMessage The error message
   * @return RedirectCheckResult indicating an error occurred
   */
  public static RedirectCheckResult error(String errorMessage) {
    return RedirectCheckResult.builder()
        .isRedirect(false)
        .error(true)
        .errorMessage(errorMessage)
        .build();
  }
}
