package org.anasoid.iptvorganizer.models.enums;

/**
 * Client-specific enum for controlling connection mode to Xtream API Includes INHERITED option to
 * inherit from source
 */
public enum ClientConnectXtreamApiMode {
  /** Inherit setting from source */
  INHERITED,
  /** Default mode - resolves to NO_PROXY */
  DEFAULT,
  /** Use no external proxy connection (reverse proxy without external proxy) */
  NO_PROXY;

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
      case NO_PROXY:
        return ConnectXtreamApiMode.NO_PROXY;
      default:
        return ConnectXtreamApiMode.DEFAULT;
    }
  }
}
