package org.anasoid.iptvorganizer.security;

import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/** SecurityContext implementation for authenticated admin token sessions. */
public class AdminSecurityContext implements SecurityContext {
  private final AdminTokenPrincipal principal;
  private final boolean secure;

  public AdminSecurityContext(AdminTokenPrincipal principal, boolean secure) {
    this.principal = principal;
    this.secure = secure;
  }

  @Override
  public Principal getUserPrincipal() {
    return principal;
  }

  @Override
  public boolean isUserInRole(String role) {
    return "admin".equals(role);
  }

  @Override
  public boolean isSecure() {
    return secure;
  }

  @Override
  public String getAuthenticationScheme() {
    return "Bearer";
  }
}
