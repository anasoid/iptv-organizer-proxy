package org.anasoid.iptvorganizer.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.AdminUser;

/** Backward-compatible token service adapter. */
@ApplicationScoped
public class JwtService {

  @Inject InMemoryTokenService tokenService;

  /** Generate admin bearer token for an authenticated user. */
  public String generateToken(AdminUser user) {
    return tokenService.issueToken(user);
  }

  /** Generate token with custom lifespan (kept for API compatibility). */
  public String generateToken(AdminUser user, long customLifespanSeconds) {
    return tokenService.issueToken(user, customLifespanSeconds);
  }
}
