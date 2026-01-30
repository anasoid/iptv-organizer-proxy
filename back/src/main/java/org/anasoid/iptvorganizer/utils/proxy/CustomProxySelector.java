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
 * <p>Converts Proxy entity configuration to Java ProxySelector by mapping ProxyType enum to
 * java.net.Proxy.Type and creating InetSocketAddress for the proxy endpoint.
 *
 * <p>Thread-safe immutable design.
 */
@Slf4j
public class CustomProxySelector extends ProxySelector {

  private final Proxy proxyEntity;
  private final java.net.Proxy javaProxy;

  /**
   * Create a CustomProxySelector from a Proxy entity.
   *
   * @param proxyEntity The proxy configuration
   * @throws IllegalArgumentException if proxy host or port is null or invalid
   */
  public CustomProxySelector(Proxy proxyEntity) {
    if (proxyEntity == null) {
      throw new IllegalArgumentException("Proxy entity cannot be null");
    }

    if (proxyEntity.getProxyHost() == null || proxyEntity.getProxyPort() == null) {
      throw new IllegalArgumentException(
          "Proxy host and port are required. Got host="
              + proxyEntity.getProxyHost()
              + ", port="
              + proxyEntity.getProxyPort());
    }

    this.proxyEntity = proxyEntity;

    // Convert ProxyType to java.net.Proxy.Type
    java.net.Proxy.Type javaProxyType = convertProxyType(proxyEntity.getProxyType());

    // Create socket address for proxy endpoint
    SocketAddress addr =
        new InetSocketAddress(proxyEntity.getProxyHost(), proxyEntity.getProxyPort());

    // Create Java Proxy instance
    this.javaProxy = new java.net.Proxy(javaProxyType, addr);

    log.debug(
        "Created CustomProxySelector for {}://{}: {}:{}",
        proxyEntity.getProxyType(),
        javaProxyType,
        proxyEntity.getProxyHost(),
        proxyEntity.getProxyPort());
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
