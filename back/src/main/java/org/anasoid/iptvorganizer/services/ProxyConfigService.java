package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.repositories.ProxyRepository;

/**
 * Service for loading proxy configuration from a Source entity. Resolution order: 1. Check if proxy
 * is explicitly disabled via enableProxy flag 2. Try to load proxy from database using Source's
 * proxyId 3. Fall back to environment variables (one per proxy attribute)
 */
@ApplicationScoped
public class ProxyConfigService {

  private static final Logger logger = Logger.getLogger(ProxyConfigService.class.getName());

  @Inject ProxyRepository proxyRepository;

  /**
   * Get proxy configuration for a given source.
   *
   * <p>Resolution logic: 1. If source.enableProxy is false, returns null 2. If source.proxyId is
   * set, attempts to load from database 3. Falls back to environment variables (one per proxy
   * attribute) 4. Returns null if no configuration is found
   *
   * @param source the Source entity to resolve proxy for
   * @return Proxy object from database or environment variables, or null if no proxy is configured
   */
  public Proxy getProxyConfig(Source source) {

    // If enableProxy is explicitly false, return null (proxy disabled)
    if (source.getEnableProxy() != null && !source.getEnableProxy()) {
      return null;
    }

    // Try to load proxy from database if proxyId is set
    if (source.getProxyId() != null) {
      Proxy proxy = proxyRepository.findById(source.getProxyId());
      if (proxy != null) {
        return proxy;
      }
      // Log warning if proxy ID references non-existent proxy
      logger.warning("Proxy ID " + source.getProxyId() + " not found in database");
    }

    // Fall back to environment variables
    return buildProxyFromEnv();
  }

  /**
   * Build a Proxy object from environment variables.
   *
   * <p>Reads the following environment variables (one per proxy attribute): - PROXY_URL (full URL
   * format) - PROXY_HOST (proxy host) - PROXY_PORT (integer port number) - PROXY_TYPE (HTTP, HTTPS,
   * or SOCKS5) - PROXY_USERNAME (proxy username) - PROXY_PASSWORD (proxy password) - PROXY_TIMEOUT
   * (integer timeout in milliseconds) - PROXY_MAX_RETRIES (integer max retry count)
   *
   * @return Proxy object with values from environment variables, or null if no env vars are set
   */
  private Proxy buildProxyFromEnv() {
    String proxyUrl = System.getenv("PROXY_URL");
    String proxyHost = System.getenv("PROXY_HOST");
    Integer proxyPort = parseIntEnv("PROXY_PORT");
    ProxyType proxyType = parseProxyTypeEnv("PROXY_TYPE");
    String proxyUsername = System.getenv("PROXY_USERNAME");
    String proxyPassword = System.getenv("PROXY_PASSWORD");
    Integer timeout = parseIntEnv("PROXY_TIMEOUT");
    Integer maxRetries = parseIntEnv("PROXY_MAX_RETRIES");

    // Return null if no environment variables are set
    if (proxyUrl == null
        && proxyHost == null
        && proxyPort == null
        && proxyType == null
        && proxyUsername == null
        && proxyPassword == null
        && timeout == null
        && maxRetries == null) {
      return null;
    }

    // Build Proxy object with available values
    return Proxy.builder()
        .proxyUrl(proxyUrl)
        .proxyHost(proxyHost)
        .proxyPort(proxyPort)
        .proxyType(proxyType)
        .proxyUsername(proxyUsername)
        .proxyPassword(proxyPassword)
        .timeout(timeout)
        .maxRetries(maxRetries)
        .build();
  }

  /**
   * Parse an environment variable as an Integer.
   *
   * @param envVarName the environment variable name
   * @return the parsed integer value, or null if not set or cannot be parsed
   */
  private Integer parseIntEnv(String envVarName) {
    String value = System.getenv(envVarName);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      // Silent failure - invalid env var is ignored
      return null;
    }
  }

  /**
   * Parse an environment variable as a ProxyType enum.
   *
   * @param envVarName the environment variable name
   * @return the parsed ProxyType, or null if not set or not a valid enum value
   */
  private ProxyType parseProxyTypeEnv(String envVarName) {
    String value = System.getenv(envVarName);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ProxyType.valueOf(value);
    } catch (IllegalArgumentException e) {
      // Silent failure - invalid enum value is ignored
      return null;
    }
  }
}
