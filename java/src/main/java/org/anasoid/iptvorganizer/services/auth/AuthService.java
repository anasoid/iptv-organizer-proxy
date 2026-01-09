package org.anasoid.iptvorganizer.services.auth;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.models.AdminUser;
import org.anasoid.iptvorganizer.repositories.AdminUserRepository;

/** Authentication service for admin user login */
@ApplicationScoped
public class AuthService {

  @Inject AdminUserRepository adminUserRepository;

  @Inject PasswordService passwordService;

  @Inject JwtService jwtService;

  /**
   * Authenticate user and return JWT token with user info
   *
   * @param username Admin username
   * @param password Plain text password
   * @return Map with token and user object
   */
  public Uni<java.util.Map<String, Object>> login(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      return Uni.createFrom().failure(new SecurityException("Username and password are required"));
    }

    return adminUserRepository
        .findByUsername(username)
        .onItem()
        .ifNull()
        .failWith(new SecurityException("Invalid username or password"))
        .onItem()
        .transform(
            user -> {
              // Check if user is active
              if (!Boolean.TRUE.equals(user.getIsActive())) {
                throw new SecurityException("Account is inactive");
              }

              // Verify password
              if (!passwordService.verifyPassword(password, user.getPasswordHash())) {
                throw new SecurityException("Invalid username or password");
              }

              return user;
            })
        .onItem()
        .transformToUni(
            user -> {
              // Update last login timestamp
              user.setLastLogin(LocalDateTime.now());
              return adminUserRepository
                  .updateLastLogin(user.getId(), user.getLastLogin())
                  .replaceWith(user);
            })
        .onItem()
        .transform(
            user -> {
              String token = jwtService.generateToken(user);
              java.util.Map<String, Object> response = new java.util.HashMap<>();
              response.put("token", token);
              response.put("user", user);
              return response;
            });
  }

  /** Get current user info from database */
  public Uni<AdminUser> getCurrentUser(Long userId) {
    if (userId == null) {
      return Uni.createFrom().failure(new IllegalArgumentException("User ID is required"));
    }
    return adminUserRepository.findById(userId);
  }
}
