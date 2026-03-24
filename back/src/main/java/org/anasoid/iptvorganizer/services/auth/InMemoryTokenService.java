package org.anasoid.iptvorganizer.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Issues and validates opaque admin bearer tokens stored in memory. */
@ApplicationScoped
public class InMemoryTokenService {

  private static final int TOKEN_SIZE_BYTES = 48;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final ConcurrentMap<String, TokenSession> tokenStore = new ConcurrentHashMap<>();

  @ConfigProperty(name = "app.auth.token.ttl.seconds", defaultValue = "28800")
  long tokenTtlSeconds;

  public String issueToken(AdminUser user) {
    return issueToken(user, tokenTtlSeconds);
  }

  public String issueToken(AdminUser user, long ttlSeconds) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("User and user ID are required");
    }

    cleanupExpiredTokens();
    String token = generateTokenValue();
    TokenSession session =
        new TokenSession(
            user.getId(),
            user.getUsername(),
            Set.of("admin"),
            Instant.now().plusSeconds(Math.max(1, ttlSeconds)));
    tokenStore.put(token, session);
    return token;
  }

  public TokenSession validateToken(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }

    TokenSession session = tokenStore.get(token);
    if (session == null) {
      return null;
    }

    if (session.expiresAt().isBefore(Instant.now())) {
      tokenStore.remove(token);
      return null;
    }

    return session;
  }

  public void revokeToken(String token) {
    if (token != null && !token.isBlank()) {
      tokenStore.remove(token);
    }
  }

  private static String generateTokenValue() {
    byte[] bytes = new byte[TOKEN_SIZE_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private void cleanupExpiredTokens() {
    Instant now = Instant.now();
    tokenStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
  }

  public record TokenSession(Long userId, String username, Set<String> roles, Instant expiresAt) {}
}
