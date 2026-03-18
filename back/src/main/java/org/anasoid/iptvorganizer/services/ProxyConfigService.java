package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.RequestType;
import org.anasoid.iptvorganizer.models.ProxyTunnelStatus;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.models.http.ProxyOptions;
import org.anasoid.iptvorganizer.repositories.ProxyRepository;

/**
 * Service for loading proxy configuration from a Source entity. Resolution order: 1. Try to load
 * proxy from database using Source's proxyId 2. Fall back to environment variables (one per proxy
 * attribute)
 */
@ApplicationScoped
@Slf4j
public class ProxyConfigService {

  @Inject ProxyRepository proxyRepository;
  @Inject ClientService clientService;

  /**
   * Get proxy configuration for a given source.
   *
   * <p>Resolution logic: 1. If source.proxyId is set, attempts to load from database 2. Falls back
   * to environment variables (one per proxy attribute) 3. Returns null if no configuration is found
   *
   * @param source the Source entity to resolve proxy for
   * @return Proxy object from database or environment variables, or null if no proxy is configured
   */
  public Proxy getProxyConfig(Source source) {
    // Try to load proxy from database if proxyId is set
    if (source.getProxyId() != null) {
      Proxy proxy = proxyRepository.findById(source.getProxyId());
      if (proxy != null) {
        return proxy;
      }
      // Log warning if proxy ID references non-existent proxy
      log.warn("Proxy ID " + source.getProxyId() + " not found in database");
    }

    // Fall back to environment variables
    return buildProxyFromEnv();
  }

  /**
   * Get proxy configuration for a given client and source, respecting enable flags.
   *
   * <p>Resolution logic: 1. Check if proxy is enabled (via client→source inheritance) 2. If
   * disabled, return null immediately 3. If enabled, load proxy configuration (database →
   * environment → null)
   *
   * @param client The client (can be null)
   * @param source The source entity to resolve proxy for
   * @return Proxy object if enabled and configured, null otherwise
   */
  public Proxy getProxyConfig(Client client, Source source, RequestType requestType) {
    // Check if proxy is enabled
    if (!resolveEnableProxy(client, source)
        || (requestType == RequestType.API && !resolveEnableProxyApi(client, source))
        || (requestType == RequestType.XML_TV && !resolveEnableProxyXmltv(client, source))
        || (requestType == RequestType.STREAM && !resolveEnableProxyStream(client, source))) {
      log.trace("proxy is disabled for client/source - returning null");
      return null;
    }

    // Proxy is enabled - delegate to existing logic
    return getProxyConfig(source);
  }

  /**
   * Get proxy configuration for a given client and source, respecting enable flags.
   *
   * <p>Resolution logic: 1. Check if proxy is enabled (via client→source inheritance) 2. If
   * disabled, return null immediately 3. If enabled, load proxy configuration (database →
   * environment → null)
   *
   * @param client The client (can be null)
   * @param source The source entity to resolve proxy for
   * @return Proxy object if enabled and configured, null otherwise
   */
  public ProxyOptions getProxyOption(Client client, Source source, RequestType requestType) {
    return new ProxyOptions(getProxyConfig(client, source, requestType));
  }

  /**
   * Check if proxy is enabled for a client and source combination.
   *
   * <p>Resolution logic (inheritance pattern): - If client value is not null: use client value - If
   * client value is null: inherit from source value - Default: false (matches schema default)
   *
   * @param client The client (can be null)
   * @param source The source (can be null)
   * @return ProxyTunnelStatus object with resolved enableProxy flag
   */
  public ProxyTunnelStatus checkProxyTunnelEnabled(Client client, Source source) {
    boolean enableProxy = resolveEnableProxy(client, source);
    return new ProxyTunnelStatus(enableProxy);
  }

  /**
   * Resolve enableProxy setting with priority: client -> source -> default false
   *
   * @param client The client (can be null)
   * @param source The source (can be null)
   * @return true if proxy is enabled
   */
  public boolean resolveEnableProxy(Client client, Source source) {
    if (client != null && client.getEnableProxy() != null) {
      return client.getEnableProxy();
    }
    if (source != null && source.getEnableProxy() != null) {
      return source.getEnableProxy();
    }
    return false;
  }

  private boolean resolveEnableProxyApi(Client client, Source source) {
    boolean enable = resolveEnableProxy(client, source);
    if (!enable) {
      return false;
    }
    ConnectXtreamApiMode mode = clientService.resolveConnectXtreamApi(client, source);
    switch (mode) {
      case NO_PROXY:
        return false;
    }
    return true;
  }

  private boolean resolveEnableProxyStream(Client client, Source source) {
    boolean enable = resolveEnableProxy(client, source);
    if (!enable) {
      return false;
    }
    ConnectXtreamStreamMode mode = clientService.resolveConnectXtreamStream(client, source);
    switch (mode) {
      case NO_PROXY:
        return false;
    }
    return true;
  }

  private boolean resolveEnableProxyXmltv(Client client, Source source) {
    boolean enable = resolveEnableProxy(client, source);
    if (!enable) {
      return false;
    }
    ConnectXmltvMode mode = clientService.resolveConnectXmltv(client, source);
    switch (mode) {
      case NO_PROXY:
        return false;
    }
    return true;
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
