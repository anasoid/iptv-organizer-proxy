package org.anasoid.iptvorganizer.utils.proxy;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.anasoid.iptvorganizer.models.entity.Proxy;

/**
 * Provides proxy authentication credentials to HttpClient.
 *
 * <p>Implements Java's Authenticator interface to supply username/password for proxy
 * authentication. Only responds to PROXY type requests, not SERVER type.
 *
 * <p>Thread-safe immutable design.
 */
public class ProxyAuthenticator extends Authenticator {

  private final String username;
  private final String password;

  /**
   * Create a ProxyAuthenticator with credentials from a Proxy entity.
   *
   * @param proxy The proxy configuration containing credentials
   */
  public ProxyAuthenticator(Proxy proxy) {
    this(proxy.getProxyUsername(), proxy.getProxyPassword());
  }

  /**
   * Create a ProxyAuthenticator with explicit credentials.
   *
   * @param username The proxy username
   * @param password The proxy password
   */
  public ProxyAuthenticator(String username, String password) {
    this.username = username;
    this.password = password;
  }

  /**
   * Get password authentication for proxy requests.
   *
   * <p>Only responds to PROXY type requests (RequestorType.PROXY), returns null for SERVER type
   * requests.
   *
   * @return PasswordAuthentication with proxy credentials, or null if request type is not PROXY
   */
  @Override
  protected PasswordAuthentication getPasswordAuthentication() {
    // Only handle PROXY type authentication, not SERVER type
    if (getRequestorType() != RequestorType.PROXY) {
      return null;
    }

    // Provide username/password for proxy authentication
    if (username != null && password != null) {
      return new PasswordAuthentication(username, password.toCharArray());
    }

    return null;
  }
}
