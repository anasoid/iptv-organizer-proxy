package org.anasoid.iptvorganizer.utils.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.models.entity.ProxyType;

/**
 * Custom ProxySelector that configures proxy routing for HttpClient.
 *
 * <p>Resolves proxy connection details in priority order:
 *
 * <ol>
 *   <li>Component fields: {@code proxyHost} + {@code proxyPort} (+ optional {@code proxyType})
 *   <li>URL field: {@code proxyUrl} parsed to extract scheme, host and port
 * </ol>
 *
 * <p>Thread-safe immutable design.
 */
@Slf4j
public class CustomProxySelector extends ProxySelector {

  private final Proxy proxyEntity;
  private final java.net.Proxy javaProxy;

  /** Internal holder for values parsed from a proxy URL. */
  private record ParsedProxy(String host, int port, ProxyType type) {}

  /**
   * Create a CustomProxySelector from a Proxy entity.
   *
   * <p>Uses component fields ({@code proxyHost}/{@code proxyPort}/{@code proxyType}) when
   * available; otherwise parses {@code proxyUrl}.
   *
   * @param proxyEntity The proxy configuration
   * @throws IllegalArgumentException if neither component fields nor proxyUrl supply enough info
   */
  public CustomProxySelector(Proxy proxyEntity) {
    if (proxyEntity == null) {
      throw new IllegalArgumentException("Proxy entity cannot be null");
    }

    this.proxyEntity = proxyEntity;

    // Resolve proxy connection details: component fields take precedence over URL
    final String resolvedHost;
    final int resolvedPort;
    final ProxyType resolvedType;

    if (proxyEntity.getProxyHost() != null && proxyEntity.getProxyPort() != null) {
      // --- Component-based configuration ---
      resolvedHost = proxyEntity.getProxyHost();
      resolvedPort = proxyEntity.getProxyPort();
      // Default to HTTP when proxyType is not explicitly set
      resolvedType =
          proxyEntity.getProxyType() != null ? proxyEntity.getProxyType() : ProxyType.HTTP;

    } else if (proxyEntity.getProxyUrl() != null && !proxyEntity.getProxyUrl().isBlank()) {
      // --- URL-based configuration ---
      ParsedProxy parsed = parseProxyUrl(proxyEntity.getProxyUrl());
      resolvedHost = parsed.host();
      resolvedPort = parsed.port();
      resolvedType = parsed.type();

    } else {
      throw new IllegalArgumentException(
          "Proxy requires either (proxyHost + proxyPort) or a non-empty proxyUrl. "
              + "Got host="
              + proxyEntity.getProxyHost()
              + ", port="
              + proxyEntity.getProxyPort()
              + ", url="
              + proxyEntity.getProxyUrl());
    }

    // Convert ProxyType to java.net.Proxy.Type and build the proxy instance
    java.net.Proxy.Type javaProxyType = convertProxyType(resolvedType);
    SocketAddress addr = new InetSocketAddress(resolvedHost, resolvedPort);
    this.javaProxy = new java.net.Proxy(javaProxyType, addr);

    log.debug(
        "Created CustomProxySelector for {}://{}: {}:{}",
        resolvedType,
        javaProxyType,
        resolvedHost,
        resolvedPort);
  }

  /**
   * Get the list of proxies for a URI.
   *
   * @param uri The target URI
   * @return List containing the configured proxy
   */
  @Override
  public List<java.net.Proxy> select(URI uri) {
    List<java.net.Proxy> proxies = new ArrayList<>();
    proxies.add(javaProxy);
    return proxies;
  }

  /**
   * Called when a connection to the proxy fails.
   *
   * <p>Logs the failure for debugging purposes.
   *
   * @param uri The URI that failed to connect
   * @param socketAddress The proxy socket address
   * @param ioe The IOException that occurred
   */
  @Override
  public void connectFailed(URI uri, SocketAddress socketAddress, IOException ioe) {
    log.warn(
        "Proxy connection failed for {}://{}: {} - {}",
        proxyEntity.getProxyType(),
        socketAddress,
        uri,
        ioe.getMessage(),
        ioe);
  }

  /**
   * Parse a proxy URL into its constituent parts.
   *
   * <p>Supported schemes and their mapped {@link ProxyType}:
   *
   * <ul>
   *   <li>{@code http} → {@link ProxyType#HTTP} (default port 8080)
   *   <li>{@code https} → {@link ProxyType#HTTPS} (default port 443)
   *   <li>{@code socks} / {@code socks5} → {@link ProxyType#SOCKS5} (default port 1080)
   * </ul>
   *
   * @param proxyUrl Raw URL string, e.g. {@code http://user:pass@proxy.example.com:8080}
   * @return Parsed host, port, and proxy type
   * @throws IllegalArgumentException if the URL cannot be parsed or the host is missing
   */
  private ParsedProxy parseProxyUrl(String proxyUrl) {
    try {
      URI uri = URI.create(proxyUrl);
      String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
      String host = uri.getHost();
      int port = uri.getPort(); // -1 when absent

      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("Cannot parse host from proxyUrl: " + proxyUrl);
      }

      final ProxyType type;
      final int defaultPort;
      if (scheme.startsWith("socks")) {
        type = ProxyType.SOCKS5;
        defaultPort = 1080;
      } else if ("https".equals(scheme)) {
        type = ProxyType.HTTPS;
        defaultPort = 443;
      } else {
        type = ProxyType.HTTP;
        defaultPort = 8080;
      }

      if (port < 0) {
        port = defaultPort;
      }

      return new ParsedProxy(host, port, type);

    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid proxyUrl: " + proxyUrl, e);
    }
  }

  /**
   * Convert ProxyType enum to java.net.Proxy.Type.
   *
   * @param proxyType The ProxyType enum value
   * @return The corresponding java.net.Proxy.Type
   * @throws IllegalArgumentException if proxy type is null or unknown
   */
  private java.net.Proxy.Type convertProxyType(ProxyType proxyType) {
    if (proxyType == null) {
      throw new IllegalArgumentException("Proxy type cannot be null");
    }

    return switch (proxyType) {
      case HTTP, HTTPS -> java.net.Proxy.Type.HTTP;
      case SOCKS5 -> java.net.Proxy.Type.SOCKS;
    };
  }
}
