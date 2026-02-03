package org.anasoid.iptvorganizer.models.enums;

/** Enum for controlling connection mode to XMLTV Used for XMLTV data fetching */
public enum ConnectXmltvMode {
  /** Return redirect to client (client fetches directly) */
  REDIRECT,
  /** Use tunnel connection (reverse proxy without external proxy) */
  TUNNEL,
  /** Use external proxy connection */
  PROXY,
  /** Default mode - resolves to same as connectXtreamStream */
  DEFAULT;

  /**
   * Resolve DEFAULT to actual mode
   *
   * @param streamMode The resolved stream mode to inherit from
   * @return The resolved mode (streamMode for DEFAULT, or self)
   */
  public ConnectXmltvMode resolve(ConnectXtreamStreamMode streamMode) {
    if (this == DEFAULT) {
      switch (streamMode) {
        case REDIRECT:
          return REDIRECT;
        case TUNNEL:
          return TUNNEL;
        case PROXY:
        case DIRECT: // Direct not applicable for XMLTV, treat as PROXY
          return PROXY;
        default:
          return PROXY;
      }
    }
    return this;
  }
}
