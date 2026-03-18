package org.anasoid.iptvorganizer.exceptions;

/**
 * Exception thrown when proxy-related errors occur during HTTP requests.
 *
 * <p>Stores proxy host and port information for context and debugging.
 */
public class ProxyException extends RuntimeException {

  private final String proxyHost;
  private final Integer proxyPort;

  public ProxyException(String message) {
    super(message);
    this.proxyHost = null;
    this.proxyPort = null;
  }

  public ProxyException(String message, Throwable cause) {
    super(message, cause);
    this.proxyHost = null;
    this.proxyPort = null;
  }

  public ProxyException(String message, String proxyHost, Integer proxyPort) {
    super(message);
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
  }

  public ProxyException(String message, Throwable cause, String proxyHost, Integer proxyPort) {
    super(message, cause);
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
  }

  @Override
  public String getMessage() {
    String base = super.getMessage();
    if (proxyHost != null && proxyPort != null) {
      return base + " [proxy: " + proxyHost + ":" + proxyPort + "]";
    }
    return base;
  }

  public String getProxyHost() {
    return proxyHost;
  }

  public Integer getProxyPort() {
    return proxyPort;
  }
}
