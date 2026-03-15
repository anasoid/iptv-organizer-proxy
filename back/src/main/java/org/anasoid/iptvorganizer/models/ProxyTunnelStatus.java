package org.anasoid.iptvorganizer.models;

/**
 * Status object containing resolved enableProxy flag.
 *
 * <p>This class holds the resolved value for the proxy enable flag, following the client→source
 * inheritance pattern.
 */
public class ProxyTunnelStatus {
  private final boolean enableProxy;

  public ProxyTunnelStatus(boolean enableProxy) {
    this.enableProxy = enableProxy;
  }

  public boolean isEnableProxy() {
    return enableProxy;
  }
}
