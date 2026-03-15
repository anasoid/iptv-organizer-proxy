package org.anasoid.iptvorganizer.models.enums;

/** Enum for controlling connection mode to XMLTV Used for XMLTV data fetching */
public enum ConnectXmltvMode {
  /** Return redirect to client (client fetches directly) */
  REDIRECT,
  /** Use tunnel connection (reverse proxy without external proxy) */
  TUNNEL,
  /** Use no external proxy connection (reverse proxy without external proxy) */
  NO_PROXY,
  /** Default mode - resolves to same as connectXtreamStream */
  DEFAULT;
}
