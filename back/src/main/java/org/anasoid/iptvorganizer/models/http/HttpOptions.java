package org.anasoid.iptvorganizer.models.http;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.models.entity.Proxy;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpOptions {
  private Boolean followRedirects;
  private Long timeout;
  private Map<String, String> headers;
  @Builder.Default private Integer maxRetries = 1;
  private Proxy proxy;
}
