package org.anasoid.iptvorganizer.models.enums;

/**
 * Enum for controlling connection mode to Xtream API Used for API calls like login, get categories,
 * get streams, etc.
 */
public enum ConnectXtreamApiMode {
  /** Default mode - resolves to NO_PROXY */
  DEFAULT,
  /** Use no external proxy connection (reverse proxy without external proxy) */
  NO_PROXY;

  /**
   * Resolve DEFAULT to actual mode
   *
   * @return The resolved mode (NO_PROXY for DEFAULT, or self)
   */
  public ConnectXtreamApiMode resolve() {
    if (this == DEFAULT) {
      return NO_PROXY;
    }
    return this;
  }
}
