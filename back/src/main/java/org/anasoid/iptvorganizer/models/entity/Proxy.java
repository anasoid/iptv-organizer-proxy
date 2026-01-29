package org.anasoid.iptvorganizer.models.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Proxy extends BaseEntity {
  // Identity
  private String name;
  private String description;

  // Unified proxy URL (optional)
  // Example: http://user:pass@proxy.example.com:8080
  private String proxyUrl;

  // Component-based HTTP proxy server configuration
  private String proxyHost;
  private Integer proxyPort;
  private ProxyType proxyType;
  private String proxyUsername;
  private String proxyPassword;

  // Connection settings
  private Integer timeout;
  private Integer maxRetries;
}
