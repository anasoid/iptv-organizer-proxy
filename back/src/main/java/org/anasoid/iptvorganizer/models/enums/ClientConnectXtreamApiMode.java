package org.anasoid.iptvorganizer.models.enums;

/**
 * Client-specific enum for controlling connection mode to Xtream API Includes INHERITED option to
 * inherit from source
 */
public enum ClientConnectXtreamApiMode {
  /** Inherit setting from source */
  INHERITED,
  /** Default mode - resolves to PROXY */
  DEFAULT,
  /** Use tunnel connection (reverse proxy without external proxy) */
  TUNNEL,
  /** Use external proxy connection */
  PROXY;

  /**
   * Convert to Source mode
   *
   * @return The equivalent source mode
   */
  public ConnectXtreamApiMode toSourceMode() {
    switch (this) {
      case INHERITED:
        throw new IllegalStateException("Cannot convert INHERITED to source mode");
      case DEFAULT:
        return ConnectXtreamApiMode.DEFAULT;
      case TUNNEL:
        return ConnectXtreamApiMode.TUNNEL;
      case PROXY:
        return ConnectXtreamApiMode.PROXY;
      default:
        return ConnectXtreamApiMode.DEFAULT;
    }
  }
}
