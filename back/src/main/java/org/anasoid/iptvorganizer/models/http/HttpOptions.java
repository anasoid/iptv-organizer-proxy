package org.anasoid.iptvorganizer.models.http;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpOptions {
  private Boolean followRedirects;
  private Long timeout;
  private Map<String, String> headers;
  private Integer maxRetries;
}
