package org.anasoid.iptvorganizer.models.enums;

/**
 * Client-specific enum for controlling connection mode to Xtream Stream Includes INHERITED option
 * to inherit from source
 */
public enum ClientConnectXtreamStreamMode {
  /** Inherit setting from source */
  INHERITED,
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
   * Convert to Source mode
   *
   * @return The equivalent source mode
   */
  public ConnectXtreamStreamMode toSourceMode() {
    switch (this) {
      case INHERITED:
        throw new IllegalStateException("Cannot convert INHERITED to source mode");
      case DIRECT:
        return ConnectXtreamStreamMode.DIRECT;
      case NO_PROXY:
        return ConnectXtreamStreamMode.NO_PROXY;
      case PROXY:
        return ConnectXtreamStreamMode.PROXY;
      case REDIRECT:
        return ConnectXtreamStreamMode.REDIRECT;
      case DEFAULT:
        return ConnectXtreamStreamMode.DEFAULT;
      default:
        return ConnectXtreamStreamMode.DEFAULT;
    }
  }
}
