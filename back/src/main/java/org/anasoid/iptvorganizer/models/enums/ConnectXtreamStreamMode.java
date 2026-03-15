package org.anasoid.iptvorganizer.models.enums;

/** Enum for controlling connection mode to Xtream Stream Used for actual streaming connections */
public enum ConnectXtreamStreamMode {
  /** Direct connection without any proxy or redirect */
  DIRECT,
  /** Use no external proxy connection (reverse proxy without external proxy) */
  NO_PROXY,
  /** Use external proxy connection */
  PROXY,
  /** Return redirect to client (client fetches directly) */
  REDIRECT,
  /** Default mode - resolves to same as connectXtreamApi */
  DEFAULT;

  /**
   * Resolve DEFAULT to actual mode
   *
   * @param apiMode The resolved API mode to inherit from
   * @return The resolved mode (apiMode for DEFAULT, or self)
   */
  public ConnectXtreamStreamMode resolve(ConnectXtreamApiMode apiMode) {
    if (this == DEFAULT) {
      // ConnectXtreamApiMode resolves to NO_PROXY which corresponds to NO_PROXY stream mode
      return ConnectXtreamStreamMode.NO_PROXY;
    }
    return this;
  }
}
