package org.anasoid.iptvorganizer.controllers.admin;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;

/** Base controller with common utility methods */
public class BaseController {

  @Inject SecurityIdentity securityIdentity;

  /** Get the current authenticated user's ID from security identity attributes. */
  protected Long getCurrentUserId() {
    if (securityIdentity == null || securityIdentity.getPrincipal() == null) {
      throw new UnauthorizedException("User not authenticated");
    }

    Long userId = securityIdentity.getAttribute("userId");
    if (userId != null) {
      return userId;
    }

    throw new UnauthorizedException("User ID not found in token");
  }

  /** Get the current authenticated user's username */
  protected String getCurrentUsername() {
    if (securityIdentity == null || securityIdentity.getPrincipal() == null) {
      throw new UnauthorizedException("User not authenticated");
    }
    return securityIdentity.getPrincipal().getName();
  }
}
