package org.anasoid.iptvorganizer.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.anasoid.iptvorganizer.services.auth.InMemoryTokenService;

/** Validates opaque bearer tokens against the in-memory token store. */
@Singleton
public class OpaqueTokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {
  private final InMemoryTokenService tokenService;

  public OpaqueTokenIdentityProvider(InMemoryTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public Class<TokenAuthenticationRequest> getRequestType() {
    return TokenAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      TokenAuthenticationRequest request, AuthenticationRequestContext context) {
    return context.runBlocking(
        () -> {
          InMemoryTokenService.TokenSession session =
              tokenService.validateToken(request.getToken().getToken());
          if (session == null) {
            throw new AuthenticationFailedException("Invalid or expired token");
          }
          return QuarkusSecurityIdentity.builder()
              .setPrincipal(new QuarkusPrincipal(session.username()))
              .addCredential(request.getToken())
              .addRoles(session.roles())
              .addAttribute("userId", session.userId())
              .addAttribute("authScheme", OpaqueTokenAuthenticationMechanism.BEARER)
              .build();
        });
  }
}
