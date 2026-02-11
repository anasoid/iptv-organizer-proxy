package org.anasoid.iptvorganizer.utils.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.Authenticator;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ProxyException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.services.ProxyConfigService;

/**
 * Factory for creating and caching HttpClient instances with proxy configuration.
 *
 * <p>Creates/caches HttpClient instances based on unique proxy configurations. Each unique
 * combination of proxy settings (host, port, type, auth, redirect policy) gets a dedicated
 * HttpClient instance to improve performance and reduce memory overhead.
 *
 * <p>Resolution order for proxy configuration: 1. Disabled via enableProxy flag → returns default
 * client 2. Database proxy (proxyId) → uses those settings 3. Environment variables (PROXY_*) →
 * uses those settings 4. No proxy configured → returns default client
 *
 * <p>Thread-safe using ConcurrentHashMap.
 */
@Slf4j
@ApplicationScoped
public class HttpClientFactory {

  private static final long DEFAULT_TIMEOUT_MS = 30000;
  // Lazy-initialized static clients (for GraalVM native image compatibility)
  private static volatile HttpClient defaultClient;
  private static volatile HttpClient defaultClientNoRedirects;

  @Inject ProxyConfigService proxyConfigService;

  // Cache: key = cacheKey, value = HttpClient instance
  private final ConcurrentHashMap<String, HttpClient> clientCache = new ConcurrentHashMap<>();

  /** Get or create default HTTP client (lazy initialization for native image) */
  private static HttpClient getDefaultClient() {
    if (defaultClient == null) {
      synchronized (HttpClientFactory.class) {
        if (defaultClient == null) {
          defaultClient =
              HttpClient.newBuilder()
                  .connectTimeout(Duration.ofMillis(DEFAULT_TIMEOUT_MS))
                  .followRedirects(HttpClient.Redirect.NORMAL)
                  .build();
        }
      }
    }
    return defaultClient;
  }

  /** Get or create default HTTP client without redirects (lazy initialization for native image) */
  private static HttpClient getDefaultClientNoRedirects() {
    if (defaultClientNoRedirects == null) {
      synchronized (HttpClientFactory.class) {
        if (defaultClientNoRedirects == null) {
          defaultClientNoRedirects =
              HttpClient.newBuilder()
                  .connectTimeout(Duration.ofMillis(DEFAULT_TIMEOUT_MS))
                  .followRedirects(HttpClient.Redirect.NEVER)
                  .build();
        }
      }
    }
    return defaultClientNoRedirects;
  }

  /**
   * Create or retrieve a cached HttpClient for the given client, source and redirect policy.
   *
   * <p>If no proxy is configured for the client/source, returns the default HttpClient.
   *
   * @param client The client entity (may override proxy settings)
   * @param source The source entity (may contain proxy configuration)
   * @param followRedirects Whether to follow HTTP redirects
   * @return HttpClient configured with proxy settings or default if no proxy
   */
  public HttpClient createClient(Client client, Source source, boolean followRedirects) {
    // Get proxy configuration for client/source, respecting enable flags
    Proxy proxyConfig = proxyConfigService.getProxyConfig(client, source);

    // If no proxy configured, return default client
    if (proxyConfig == null) {
      log.debug("No proxy configured for client/source, using default HttpClient");
      return followRedirects ? getDefaultClient() : getDefaultClientNoRedirects();
    }

    // Build cache key from proxy configuration
    String cacheKey = buildCacheKey(proxyConfig, followRedirects);

    // Return cached client or create new one
    return clientCache.computeIfAbsent(
        cacheKey, key -> createClientWithProxy(proxyConfig, followRedirects));
  }

  /**
   * Create or retrieve a cached HttpClient for the given source and redirect policy.
   *
   * <p>If no proxy is configured for the source, returns the default HttpClient.
   *
   * <p><strong>Deprecated:</strong> Use {@link #createClient(Client, Source, boolean)} to respect
   * client-level enable flags. This method is maintained for backward compatibility.
   *
   * @param source The source entity (may contain proxy configuration)
   * @param followRedirects Whether to follow HTTP redirects
   * @return HttpClient configured with proxy settings or default if no proxy
   */
  public HttpClient createClient(Source source, boolean followRedirects) {
    return createClient(null, source, followRedirects);
  }

  /**
   * Create a new HttpClient with the given proxy configuration.
   *
   * @param proxy The proxy configuration
   * @param followRedirects Whether to follow HTTP redirects
   * @return Configured HttpClient instance
   */
  public HttpClient createClientWithProxy(Proxy proxy, boolean followRedirects) {
    try {
      HttpClient.Builder builder = HttpClient.newBuilder();

      // Set redirect policy
      HttpClient.Redirect redirectPolicy =
          followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER;
      builder.followRedirects(redirectPolicy);

      // Set timeout from proxy config or use default
      Duration timeout =
          proxy.getTimeout() != null
              ? Duration.ofMillis(proxy.getTimeout())
              : Duration.ofMillis(DEFAULT_TIMEOUT_MS);
      builder.connectTimeout(timeout);

      // Configure proxy selector
      builder.proxy(new CustomProxySelector(proxy));

      // Configure proxy authenticator if credentials provided
      if (proxy.getProxyUsername() != null && proxy.getProxyPassword() != null) {
        Authenticator authenticator = new ProxyAuthenticator(proxy);
        builder.authenticator(authenticator);
        log.debug(
            "Created HttpClient with proxy: {}:{} (with authentication)",
            proxy.getProxyHost(),
            proxy.getProxyPort());
      } else {
        log.debug(
            "Created HttpClient with proxy: {}:{} (no authentication)",
            proxy.getProxyHost(),
            proxy.getProxyPort());
      }

      return builder.build();
    } catch (Exception e) {
      log.error(
          "Failed to create HttpClient with proxy {}:{}: {}",
          proxy.getProxyHost(),
          proxy.getProxyPort(),
          e.getMessage(),
          e);
      throw new ProxyException(
          "Failed to create HttpClient with proxy configuration",
          e,
          proxy.getProxyHost(),
          proxy.getProxyPort());
    }
  }

  /**
   * Build a cache key from proxy configuration and redirect policy.
   *
   * <p>Key format: host:port:type:hasAuth:followRedirects
   *
   * @param proxy The proxy configuration
   * @param followRedirects Whether to follow redirects
   * @return Unique cache key
   */
  private String buildCacheKey(Proxy proxy, boolean followRedirects) {
    boolean hasAuth = proxy.getProxyUsername() != null && proxy.getProxyPassword() != null;
    return String.format(
        "%s:%d:%s:%s:%s",
        proxy.getProxyHost(), proxy.getProxyPort(), proxy.getProxyType(), hasAuth, followRedirects);
  }

  /**
   * Clear the HttpClient cache.
   *
   * <p>Useful for testing or when proxy configuration changes.
   */
  public void clearCache() {
    clientCache.clear();
    log.debug("Cleared HttpClient cache");
  }

  /**
   * Get the number of cached HttpClient instances.
   *
   * @return The current size of the cache
   */
  public int getCacheSize() {
    return clientCache.size();
  }
}
