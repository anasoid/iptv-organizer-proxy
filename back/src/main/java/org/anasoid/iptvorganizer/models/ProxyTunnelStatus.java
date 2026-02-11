package org.anasoid.iptvorganizer.models;

/**
 * Status object containing resolved enableProxy and enableTunnel flags.
 *
 * <p>This class holds the resolved values for proxy and tunnel enable flags, following the
 * client→source inheritance pattern.
 */
public class ProxyTunnelStatus {
  private final boolean enableProxy;
  private final boolean enableTunnel;

  public ProxyTunnelStatus(boolean enableProxy, boolean enableTunnel) {
    this.enableProxy = enableProxy;
    this.enableTunnel = enableTunnel;
  }

  public boolean isEnableProxy() {
    return enableProxy;
  }

  public boolean isEnableTunnel() {
    return enableTunnel;
  }

  /**
   * Convenience method to check if both proxy and tunnel are enabled.
   *
   * @return true if both proxy and tunnel are enabled
   */
  public boolean isBothEnabled() {
    return enableProxy && enableTunnel;
  }

  /**
   * Convenience method to check if either proxy or tunnel is enabled.
   *
   * @return true if either proxy or tunnel is enabled
   */
  public boolean isAnyEnabled() {
    return enableProxy || enableTunnel;
  }
}
