package org.anasoid.iptvorganizer.services.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** JWT token generation service using SmallRye JWT */
@ApplicationScoped
public class JwtService {

  @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
  String issuer;

  @ConfigProperty(name = "smallrye.jwt.new-token.lifespan", defaultValue = "28800")
  Long lifespan;

  /** Generate JWT token for authenticated admin user */
  public String generateToken(AdminUser user) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("User and user ID are required");
    }

    return Jwt.issuer(issuer)
        .upn(user.getUsername())
        .groups(Set.of("admin"))
        .claim("userId", user.getId())
        .claim("email", user.getEmail())
        .expiresIn(lifespan)
        .sign();
  }

  /** Generate token with custom lifespan (in seconds) */
  public String generateToken(AdminUser user, long customLifespanSeconds) {
    if (user == null || user.getId() == null) {
      throw new IllegalArgumentException("User and user ID are required");
    }

    return Jwt.issuer(issuer)
        .upn(user.getUsername())
        .groups(Set.of("admin"))
        .claim("userId", user.getId())
        .claim("email", user.getEmail())
        .expiresIn(customLifespanSeconds)
        .sign();
  }
}
