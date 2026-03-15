package org.anasoid.iptvorganizer.models.enums;

/**
 * Client-specific enum for controlling connection mode to XMLTV Includes INHERITED option to
 * inherit from source
 */
public enum ClientConnectXmltvMode {
  /** Inherit setting from source */
  INHERITED,
  /** Return redirect to client (client fetches directly) */
  REDIRECT,
  /** Use tunnel connection (reverse proxy without external proxy) */
  TUNNEL,
  /** Use no external proxy connection (reverse proxy without external proxy) */
  NO_PROXY,
  /** Default mode - resolves to same as connectXtreamStream */
  DEFAULT;

  /**
   * Convert to Source mode
   *
   * @return The equivalent source mode
   */
  public ConnectXmltvMode toSourceMode() {
    switch (this) {
      case INHERITED:
        throw new IllegalStateException("Cannot convert INHERITED to source mode");
      case REDIRECT:
        return ConnectXmltvMode.REDIRECT;
      case TUNNEL:
        return ConnectXmltvMode.TUNNEL;
      case NO_PROXY:
        return ConnectXmltvMode.NO_PROXY;
      case DEFAULT:
        return ConnectXmltvMode.DEFAULT;
      default:
        return ConnectXmltvMode.DEFAULT;
    }
  }
}
