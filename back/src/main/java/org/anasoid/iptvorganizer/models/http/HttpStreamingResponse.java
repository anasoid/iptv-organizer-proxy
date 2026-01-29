package org.anasoid.iptvorganizer.models.http;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wraps HTTP response with status code, headers, and body stream.
 *
 * <p>This model allows streaming responses to propagate both headers and body to the client, unlike
 * the simple InputStream returned by streamHttp().
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpStreamingResponse {
  private int statusCode;
  private Map<String, List<String>> headers;
  private InputStream body;

  /**
   * Get first value of a header by name (case-insensitive key lookup).
   *
   * @param name Header name
   * @return Header value or null if not present
   */
  public String getHeader(String name) {
    if (headers == null) {
      return null;
    }
    List<String> values = headers.get(name);
    return (values != null && !values.isEmpty()) ? values.get(0) : null;
  }
}
