package org.anasoid.iptvorganizer.controllers;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;

/** Base controller with common utility methods */
public class BaseController {

  @Inject SecurityIdentity securityIdentity;

  /** Get the current authenticated user's ID from JWT claims */
  protected Long getCurrentUserId() {
    if (securityIdentity == null || securityIdentity.getPrincipal() == null) {
      throw new UnauthorizedException("User not authenticated");
    }
    String userId = securityIdentity.getAttribute("userId");
    if (userId == null) {
      throw new UnauthorizedException("User ID not found in token");
    }
    try {
      return Long.parseLong(userId.toString());
    } catch (NumberFormatException e) {
      throw new UnauthorizedException("Invalid user ID in token");
    }
  }

  /** Get the current authenticated user's username */
  protected String getCurrentUsername() {
    if (securityIdentity == null || securityIdentity.getPrincipal() == null) {
      throw new UnauthorizedException("User not authenticated");
    }
    return securityIdentity.getPrincipal().getName();
  }
}
