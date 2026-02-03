package org.anasoid.iptvorganizer.models.enums;

/**
 * Enum for controlling connection mode to Xtream API Used for API calls like login, get categories,
 * get streams, etc.
 */
public enum ConnectXtreamApiMode {
  /** Default mode - resolves to PROXY */
  DEFAULT,
  /** Use tunnel connection (reverse proxy without external proxy) */
  TUNNEL,
  /** Use external proxy connection */
  PROXY;

  /**
   * Resolve DEFAULT to actual mode
   *
   * @return The resolved mode (PROXY for DEFAULT, or self)
   */
  public ConnectXtreamApiMode resolve() {
    if (this == DEFAULT) {
      return PROXY;
    }
    return this;
  }
}
