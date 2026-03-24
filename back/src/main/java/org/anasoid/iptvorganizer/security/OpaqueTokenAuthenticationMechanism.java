package org.anasoid.iptvorganizer.security;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/** Quarkus HTTP authentication mechanism for opaque in-memory bearer tokens. */
@ApplicationScoped
public class OpaqueTokenAuthenticationMechanism implements HttpAuthenticationMechanism {

  static final String BEARER = "Bearer";

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String authorizationHeader = context.request().headers().get(HttpHeaderNames.AUTHORIZATION);
    String token = extractBearerToken(authorizationHeader);
    if (token == null) {
      return Uni.createFrom().optional(Optional.empty());
    }

    context.put(HttpAuthenticationMechanism.class.getName(), this);
    TokenCredential credential = new TokenCredential(token, BEARER);
    TokenAuthenticationRequest request = new TokenAuthenticationRequest(credential);
    return identityProviderManager.authenticate(
        HttpSecurityUtils.setRoutingContextAttribute(request, context));
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom()
        .item(
            new ChallengeData(
                HttpResponseStatus.UNAUTHORIZED.code(), HttpHeaderNames.WWW_AUTHENTICATE, BEARER));
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Collections.singleton(TokenAuthenticationRequest.class);
  }

  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    return Uni.createFrom()
        .item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, BEARER));
  }

  @Override
  public int getPriority() {
    return 3000;
  }

  public static String extractBearerToken(String authorizationHeader) {
    if (authorizationHeader == null) {
      return null;
    }

    String prefix = BEARER + " ";
    if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }

    String token = authorizationHeader.substring(prefix.length()).trim();
    if (token.isBlank()) {
      throw new AuthenticationFailedException("Bearer token is empty");
    }

    return token;
  }
}
