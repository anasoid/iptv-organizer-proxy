package org.anasoid.iptvorganizer.config;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * WebClientConfig - No longer needed as HttpStreamingService uses Java 11+ HttpClient. Kept for
 * compatibility.
 */
@ApplicationScoped
public class WebClientConfig {
  // HTTP client configuration is now handled by HttpStreamingService using java.net.http.HttpClient
}
