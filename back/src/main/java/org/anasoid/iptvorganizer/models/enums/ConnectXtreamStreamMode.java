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
}
