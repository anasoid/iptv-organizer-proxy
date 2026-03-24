package org.anasoid.iptvorganizer.security;

/** Legacy bearer-token parsing helper kept for backward compatibility. */
public final class AdminTokenAuthenticationFilter {

  private AdminTokenAuthenticationFilter() {}

  public static String extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null) {
      return null;
    }
    if (!authorizationHeader.startsWith("Bearer ")) {
      return null;
    }
    String token = authorizationHeader.substring(7).trim();
    return token.isBlank() ? null : token;
  }
}
