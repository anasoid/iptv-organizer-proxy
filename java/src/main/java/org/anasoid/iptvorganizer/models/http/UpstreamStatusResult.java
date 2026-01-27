package org.anasoid.iptvorganizer.models.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of upstream status check (HEAD request).
 *
 * <p>Used to detect redirects and errors early before streaming the full body, matching the PHP
 * implementation's checkUpstreamStatus() behavior.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpstreamStatusResult {
  private int statusCode;
  private String location;
  private boolean error;
  private String errorMessage;

  /**
   * Check if response is a redirect status code (301, 302, 307).
   *
   * @return true if status code indicates a redirect
   */
  public boolean isRedirect() {
    return statusCode == 302 || statusCode == 301 || statusCode == 307;
  }
}
