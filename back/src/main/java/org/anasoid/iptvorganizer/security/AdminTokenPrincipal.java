package org.anasoid.iptvorganizer.security;

import java.security.Principal;

/** Principal backed by the in-memory admin token session. */
public class AdminTokenPrincipal implements Principal {
  private final Long userId;
  private final String username;

  public AdminTokenPrincipal(Long userId, String username) {
    this.userId = userId;
    this.username = username;
  }

  public Long getUserId() {
    return userId;
  }

  @Override
  public String getName() {
    return username;
  }
}
